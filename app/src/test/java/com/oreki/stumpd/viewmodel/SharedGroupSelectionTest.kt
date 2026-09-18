package com.oreki.stumpd.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.local.entity.GroupDefaultEntity
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The group filter is app-wide: whatever is picked on one screen is what the next screen opens on.
 *
 * The all-players list used to read a `default_group_id` SharedPreference that nothing writes any
 * more — the selection lives in the database — so it always opened on "All Groups", and a group's
 * own settings (like its milestone score) were never applied.
 */
@RequiresApi(Build.VERSION_CODES.O)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SharedGroupSelectionTest {

    private val matchRepo = mockk<MatchRepository>()
    private val playerRepo = mockk<PlayerRepository>()
    private val groupRepo = mockk<GroupRepository>(relaxed = true)

    @Before
    fun setup() {
        coEvery { matchRepo.getAllMatches() } returns emptyList()
        coEvery { playerRepo.getPlayerDetailedStats(any(), any()) } returns emptyList()
        coEvery { groupRepo.listGroupSummaries() } returns listOf(
            Triple(group("g1", "Hogwarts"), defaults("g1", battingMilestone = 15), 0),
            Triple(group("g2", "Kasturi Nivasa"), defaults("g2", battingMilestone = 20), 0),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun group(id: String, name: String) = GroupEntity(id = id, name = name)

    private fun defaults(groupId: String, battingMilestone: Int) = GroupDefaultEntity(
        groupId = groupId,
        groundName = "",
        format = "WHITE_BALL",
        shortPitch = false,
        matchSettingsJson = """{"battingMilestone":$battingMilestone,"totalOvers":5}""",
    )

    private fun viewModel(groupId: String? = null, groupName: String = "") =
        AllPlayersStatsViewModel(
            initialGroupId = groupId,
            initialGroupName = groupName,
            initialPitchType = null,
            initialDateFilter = "All Time",
            initialSortBy = "Runs",
            matchRepository = matchRepo,
            playerRepository = playerRepo,
            groupRepository = groupRepo,
        )

    @Test
    fun `opens on the group the app is filtered to`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { groupRepo.getDefaultGroupId() } returns "g1"

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.selectedGroupId).isEqualTo("g1")
        assertThat(vm.selectedGroupName).isEqualTo("Hogwarts")
    }

    @Test
    fun `that group's milestone is the one the stats count`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { groupRepo.getDefaultGroupId() } returns "g1"

        val vm = viewModel()
        advanceUntilIdle()

        // Hogwarts is set to 15; without the shared selection this read 20.
        assertThat(vm.battingMilestone).isEqualTo(15)
    }

    @Test
    fun `a group passed in by another screen wins over the stored one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { groupRepo.getDefaultGroupId() } returns "g1"

        val vm = viewModel(groupId = "g2", groupName = "Kasturi Nivasa")
        advanceUntilIdle()

        assertThat(vm.selectedGroupId).isEqualTo("g2")
        assertThat(vm.battingMilestone).isEqualTo(20)
    }

    @Test
    fun `no stored selection means all groups`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { groupRepo.getDefaultGroupId() } returns null

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.selectedGroupId).isNull()
    }

    @Test
    fun `a stored group that no longer exists is ignored`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { groupRepo.getDefaultGroupId() } returns "deleted-group"

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.selectedGroupId).isNull()
    }

    @Test
    fun `changing the group here is remembered for other screens`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { groupRepo.getDefaultGroupId() } returns "g1"

        val vm = viewModel()
        advanceUntilIdle()
        vm.updateSelectedGroup("g2", "Kasturi Nivasa")
        advanceUntilIdle()

        coVerify { groupRepo.setSelectedGroupId("g2") }
        assertThat(vm.battingMilestone).isEqualTo(20)
    }

    @Test
    fun `switching to all groups is remembered too`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { groupRepo.getDefaultGroupId() } returns "g1"

        val vm = viewModel()
        advanceUntilIdle()
        vm.updateSelectedGroup(null, "All Groups")
        advanceUntilIdle()

        coVerify { groupRepo.setSelectedGroupId(null) }
    }
}
