package com.oreki.stumpd.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import com.oreki.stumpd.FieldingFilter
import com.oreki.stumpd.RecordCategory
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.PlayerMatchStats
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordsViewModelTest {

    private val matchRepo = mockk<MatchRepository>()
    private val groupRepo = mockk<GroupRepository>()

    @Before
    fun setup() {
        // No group stored: these tests exercise the fall back to the first group.
        coEvery { groupRepo.getDefaultGroupId() } returns null
        coEvery { groupRepo.setSelectedGroupId(any()) } returns Unit
        coEvery { groupRepo.listGroups() } returns listOf(
            GroupEntity(id = "g1", name = "Group 1"),
            GroupEntity(id = "g2", name = "Group 2"),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun batRow(name: String, runs: Int, team: String = "A") = PlayerMatchStats(
        id = name,
        name = name,
        runs = runs,
        ballsFaced = runs.coerceAtLeast(1),
        fours = 0,
        sixes = 0,
        team = team,
        role = "BAT",
    )

    private fun bowlRow(name: String, wickets: Int, runsConceded: Int, team: String = "B") = PlayerMatchStats(
        id = name,
        name = name,
        wickets = wickets,
        runsConceded = runsConceded,
        oversBowled = 4.0,
        team = team,
        role = "BOWL",
    )

    private fun matchWithStats(
        id: String,
        groupId: String? = "g1",
        shortPitch: Boolean = false,
        battingRuns: Int = 80,
    ) = MatchHistory(
        id = id,
        team1Name = "A",
        team2Name = "B",
        firstInningsRuns = battingRuns,
        firstInningsWickets = 0,
        secondInningsRuns = 70,
        secondInningsWickets = 1,
        winnerTeam = "A",
        winningMargin = "10 runs",
        groupId = groupId,
        shortPitch = shortPitch,
        firstInningsBatting = listOf(batRow("Opener", battingRuns, "A")),
        secondInningsBatting = emptyList(),
        firstInningsBowling = listOf(bowlRow("Pace", 2, 30, "B")),
        secondInningsBowling = emptyList(),
    )

    private fun matchWithCatches(id: String, catches: Int) = MatchHistory(
        id = id,
        team1Name = "A",
        team2Name = "B",
        firstInningsRuns = 20,
        firstInningsWickets = 0,
        secondInningsRuns = 18,
        secondInningsWickets = 1,
        winnerTeam = "A",
        winningMargin = "2 runs",
        groupId = "g1",
        shortPitch = false,
        firstInningsBatting = emptyList(),
        secondInningsBatting = emptyList(),
        firstInningsBowling = listOf(
            bowlRow("F1", 0, 20, "B").copy(catches = catches),
        ),
        secondInningsBowling = emptyList(),
    )

    @Test
    fun loadData_fillsAllMatchesAndSelectsFirstGroup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val m = matchWithStats("m1")
        coEvery { matchRepo.getAllMatchesWithStats() } returns listOf(m)

        val vm = RecordsViewModel(RuntimeEnvironment.getApplication(), matchRepo, groupRepo)
        advanceUntilIdle()
        assertThat(vm.isLoading).isFalse()
        assertThat(vm.allMatches).containsExactly(m)
        assertThat(vm.selectedGroupId).isEqualTo("g1")
        assertThat(vm.selectedGroupName).isEqualTo("Group 1")
        assertThat(vm.records).isNotEmpty()
    }

    @Test
    fun onCategorySelected_switchesCategoryAndRecalculates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val m = matchWithStats("m1", battingRuns = 50)
        coEvery { matchRepo.getAllMatchesWithStats() } returns listOf(m)
        val vm = RecordsViewModel(RuntimeEnvironment.getApplication(), matchRepo, groupRepo)
        advanceUntilIdle()
        val battingCount = vm.records.size
        assertThat(battingCount).isAtLeast(1)
        assertThat(vm.selectedCategory).isEqualTo(RecordCategory.BattingRecords)

        vm.onCategorySelected(RecordCategory.BowlingRecords)
        advanceUntilIdle()
        assertThat(vm.selectedCategory).isEqualTo(RecordCategory.BowlingRecords)
        assertThat(vm.records).isNotEmpty()
    }

    @Test
    fun onGroupSelected_limitsMatchesUsedForRecords() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val inG1 = matchWithStats("a", groupId = "g1", battingRuns = 200)
        val inG2 = matchWithStats("b", groupId = "g2", battingRuns = 5)
        coEvery { matchRepo.getAllMatchesWithStats() } returns listOf(inG1, inG2)
        val vm = RecordsViewModel(RuntimeEnvironment.getApplication(), matchRepo, groupRepo)
        advanceUntilIdle()
        val topWhenG1 = vm.records.find { it.title == "Highest Individual Score" }

        vm.onGroupSelected("g2", "Group 2")
        advanceUntilIdle()
        assertThat(vm.selectedGroupId).isEqualTo("g2")
        val topWhenG2 = vm.records.find { it.title == "Highest Individual Score" }
        // The trailing "*" is cricket's not-out marker: batRow() leaves isOut false, so the
        // record is correctly reported as an unbeaten score.
        assertThat(topWhenG1!!.value).isEqualTo("200*")
        assertThat(topWhenG2!!.value).isEqualTo("5*")
    }

    @Test
    fun onFilterSelected_customRange_filtersMatches() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val zone = ZoneId.systemDefault()
        val inside = matchWithStats(
            "in",
            battingRuns = 99,
            shortPitch = false,
        ).copy(
            matchDate = LocalDate.of(2024, 7, 10).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        val outside = matchWithStats(
            "out",
            battingRuns = 200,
            shortPitch = false,
        ).copy(
            matchDate = LocalDate.of(2020, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        coEvery { matchRepo.getAllMatchesWithStats() } returns listOf(inside, outside)
        val vm = RecordsViewModel(RuntimeEnvironment.getApplication(), matchRepo, groupRepo)
        advanceUntilIdle()
        val start = LocalDate.of(2024, 7, 1)
        val end = LocalDate.of(2024, 7, 31)
        vm.onFilterSelected("Custom", start, end)
        advanceUntilIdle()
        assertThat(vm.selectedFilter).isEqualTo("Custom")
        assertThat(vm.startDate).isEqualTo(start)
        assertThat(vm.endDate).isEqualTo(end)
        val highest = vm.records.find { it.title == "Highest Individual Score" }
        assertThat(highest?.matchId).isEqualTo("in")
    }

    @Test
    fun onPitchTypeSelected_andFieldingFilter_changeRecords() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val longM = matchWithCatches("long", catches = 4).copy(shortPitch = false)
        val shortM = matchWithCatches("short", catches = 1).copy(shortPitch = true)
        coEvery { matchRepo.getAllMatchesWithStats() } returns listOf(longM, shortM)
        val vm = RecordsViewModel(RuntimeEnvironment.getApplication(), matchRepo, groupRepo)
        advanceUntilIdle()

        vm.onCategorySelected(RecordCategory.FieldingRecords)
        advanceUntilIdle()
        val allCatchesRecord = vm.records.find { it.title == "Most Catches in a Match" }
        assertThat(allCatchesRecord?.value).isEqualTo("4")

        vm.onPitchTypeSelected(true)
        advanceUntilIdle()
        val afterPitch = vm.records.find { it.title == "Most Catches in a Match" }
        assertThat(afterPitch?.value).isEqualTo("1")

        vm.onFieldingFilterSelected(FieldingFilter.RUN_OUTS)
        advanceUntilIdle()
        assertThat(vm.fieldingFilter).isEqualTo(FieldingFilter.RUN_OUTS)
    }
}

