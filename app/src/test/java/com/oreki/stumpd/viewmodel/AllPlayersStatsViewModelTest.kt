package com.oreki.stumpd.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.manager.PlayerDetailedStats
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.domain.model.MatchHistory
import io.mockk.coEvery
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * The group / pitch / date filters behind the all-players list.
 *
 * These assertions came from `StatsViewModelTest`; that screen showed the top five by runs and by
 * wickets — both subsets of this list — and was retired, but it re-implemented the same filtering,
 * so the coverage moved here rather than going away with it.
 */
@RequiresApi(Build.VERSION_CODES.O)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AllPlayersStatsViewModelTest {

    private val matchRepo = mockk<MatchRepository>()
    private val playerRepo = mockk<PlayerRepository>()
    private val groupRepo = mockk<GroupRepository>()

    /** The match list the player stats were last computed from. */
    private var statsInput: List<MatchHistory> = emptyList()

    @Before
    fun setup() {
        coEvery { groupRepo.listGroupSummaries() } returns emptyList()
        // The screen now opens on the app-wide group selection and records changes to it.
        coEvery { groupRepo.getDefaultGroupId() } returns null
        coEvery { groupRepo.setSelectedGroupId(any()) } returns Unit
        coEvery { playerRepo.getPlayerDetailedStats(any(), any()) } answers {
            statsInput = firstArg()
            emptyList()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleMatch(
        id: String,
        groupId: String? = null,
        shortPitch: Boolean = false,
        playerOfTheMatchName: String? = null,
        matchDate: Long = LocalDate.of(2024, 6, 10)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    ) = MatchHistory(
        id = id,
        team1Name = "A",
        team2Name = "B",
        firstInningsRuns = 30,
        firstInningsWickets = 1,
        secondInningsRuns = 28,
        secondInningsWickets = 2,
        winnerTeam = "A",
        winningMargin = "2 runs",
        matchDate = matchDate,
        groupId = groupId,
        shortPitch = shortPitch,
        playerOfTheMatchName = playerOfTheMatchName,
    )

    private fun viewModel(
        groupId: String? = null,
        pitchType: Boolean? = null,
        dateFilter: String = "All Time",
        sortBy: String = "Runs",
    ) = AllPlayersStatsViewModel(
        initialGroupId = groupId,
        initialGroupName = "All Groups",
        initialPitchType = pitchType,
        initialDateFilter = dateFilter,
        initialSortBy = sortBy,
        matchRepository = matchRepo,
        playerRepository = playerRepo,
        groupRepository = groupRepo,
    )

    private fun content(vm: AllPlayersStatsViewModel) =
        vm.uiState as AllPlayersStatsViewModel.UiState.Content

    @Test
    fun `loads players and leaves the loading state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { matchRepo.getAllMatches() } returns listOf(sampleMatch("1"), sampleMatch("2"))
        coEvery { playerRepo.getPlayerDetailedStats(any(), any()) } returns listOf(
            PlayerDetailedStats(playerId = "p1", name = "Alice"),
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(content(vm).players).hasSize(1)
    }

    @Test
    fun `selecting a group narrows the matches the stats are built from`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val g1 = sampleMatch("a", groupId = "g1")
        val g2 = sampleMatch("b", groupId = "g2")
        coEvery { matchRepo.getAllMatches() } returns listOf(g1, g2)

        val vm = viewModel()
        advanceUntilIdle()
        assertThat(content(vm).baseMatchesForFilter).containsExactly(g1, g2)

        vm.updateSelectedGroup("g1", "One")
        advanceUntilIdle()

        assertThat(vm.selectedGroupId).isEqualTo("g1")
        assertThat(content(vm).baseMatchesForFilter).containsExactly(g1)
    }

    @Test
    fun `the pitch filter matches on the short pitch flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val longPitch = sampleMatch("l", shortPitch = false)
        val short = sampleMatch("s", shortPitch = true)
        coEvery { matchRepo.getAllMatches() } returns listOf(longPitch, short)

        val vm = viewModel(pitchType = false)
        advanceUntilIdle()
        assertThat(content(vm).baseMatchesForFilter).containsExactly(longPitch)

        vm.updatePitchType(true)
        advanceUntilIdle()

        assertThat(vm.selectedPitchType).isEqualTo(true)
        assertThat(content(vm).baseMatchesForFilter).containsExactly(short)
    }

    @Test
    fun `a Date filter keeps only that day`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val zone = ZoneId.systemDefault()
        val day = LocalDate.of(2025, 3, 1)
        val onDay = sampleMatch("d1", matchDate = day.atStartOfDay(zone).toInstant().toEpochMilli())
        val otherDay = sampleMatch(
            "d2",
            matchDate = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        coEvery { matchRepo.getAllMatches() } returns listOf(onDay, otherDay)

        val vm = viewModel()
        advanceUntilIdle()
        vm.updateDateFilter("Date:$day")
        advanceUntilIdle()

        assertThat(vm.selectedFilter).isEqualTo("Date:$day")
        // baseMatchesForFilter is captured before the date filter is applied, so assert on the
        // list the player stats were actually computed from.
        assertThat(statsInput).containsExactly(onDay)
    }

    @Test
    fun `a CustomRange filter keeps the matches inside the range`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2025, 1, 31)
        val inside = sampleMatch(
            "in",
            matchDate = LocalDate.of(2025, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        val outside = sampleMatch(
            "out",
            matchDate = end.plusDays(5).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        coEvery { matchRepo.getAllMatches() } returns listOf(inside, outside)

        val vm = viewModel()
        advanceUntilIdle()
        vm.updateDateFilter("CustomRange:$start|$end")
        advanceUntilIdle()

        assertThat(vm.selectedFilter).isEqualTo("CustomRange:$start|$end")
        assertThat(statsInput).containsExactly(inside)
    }

    @Test
    fun `relative date filters are not carried in, since the picker cannot show them`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { matchRepo.getAllMatches() } returns listOf(sampleMatch("1"))

        val vm = viewModel(dateFilter = "This Week")
        advanceUntilIdle()

        assertThat(vm.selectedFilter).isEqualTo("All Time")
    }

    @Test
    fun `player of the match awards are counted from the filtered matches`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { matchRepo.getAllMatches() } returns listOf(
            sampleMatch("1", groupId = "g1", playerOfTheMatchName = "Samhith"),
            sampleMatch("2", groupId = "g1", playerOfTheMatchName = "Samhith"),
            sampleMatch("3", groupId = "g2", playerOfTheMatchName = "Kushal"),
            sampleMatch("4", groupId = "g1", playerOfTheMatchName = null),
        )

        val vm = viewModel(groupId = "g1")
        advanceUntilIdle()

        // Kushal's award was in another group, so it isn't counted here.
        assertThat(content(vm).potmCounts).containsExactly("Samhith", 2)
    }

}
