package com.oreki.stumpd.data.repository

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.local.dao.MatchDao
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.domain.match.CorrectionOutcome
import com.oreki.stumpd.domain.match.MatchCorrection
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.WicketType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The write path: preview, then commit under a lock.
 *
 * The lock is the part worth testing hardest. Between previewing a correction and confirming it,
 * a cloud sync can land and replace the match — and applying a confirmed diff to different
 * figures is precisely how a correction feature would corrupt data while looking like it worked.
 */
@RunWith(RobolectricTestRunner::class)
class MatchCorrectionRepositoryTest {

    private val db: StumpdDb = mockk(relaxed = true)
    private val matchDao: MatchDao = mockk(relaxed = true)
    private val matchRepository: MatchRepository = mockk(relaxed = true)
    private val groupRepository: GroupRepository = mockk(relaxed = true)
    private lateinit var repository: MatchCorrectionRepository

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5)

    @Before
    fun setup() {
        every { db.matchDao() } returns matchDao
        repository = MatchCorrectionRepository(
            db = db,
            matchRepository = matchRepository,
            groupRepository = groupRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    private fun match(groupId: String? = null) = MatchHistory(
        id = "m1",
        team1Name = "Strikers",
        team2Name = "Chasers",
        firstInningsRuns = 20,
        firstInningsWickets = 1,
        secondInningsRuns = 12,
        secondInningsWickets = 0,
        winnerTeam = "Strikers",
        winningMargin = "8 runs",
        groupId = groupId,
        groupName = groupId?.let { "Sunday League" },
        firstInningsBatting = listOf(
            PlayerMatchStats(
                id = "kushal", name = "Kushal", team = "Strikers", role = "BAT",
                runs = 8, ballsFaced = 7, isOut = true, dismissalType = "CAUGHT",
                bowlerName = "Muttu", fielderName = "Madhu",
            ),
            PlayerMatchStats(id = "gokul", name = "Gokul", team = "Strikers", role = "BAT", runs = 12, ballsFaced = 9),
        ),
        firstInningsBowling = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BOWL", wickets = 1, runsConceded = 20, oversBowled = 3.0),
            PlayerMatchStats(id = "madhu", name = "Madhu", team = "Chasers", role = "BOWL", oversBowled = 0.0, catches = 1),
        ),
        secondInningsBatting = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BAT", runs = 12, ballsFaced = 10),
        ),
        secondInningsBowling = listOf(
            PlayerMatchStats(id = "gokul", name = "Gokul", team = "Strikers", role = "BOWL", runsConceded = 12, oversBowled = 2.0),
        ),
        firstInningsPartnerships = listOf(
            Partnership("Kushal", "Gokul", runs = 8, balls = 7, isActive = false),
            Partnership("Gokul", "Ajith", runs = 12, balls = 9, isActive = true),
        ),
        firstInningsFallOfWickets = listOf(
            FallOfWicket(
                batsmanName = "Kushal", runs = 8, overs = 1.1, wicketNumber = 1,
                dismissalType = "CAUGHT", bowlerName = "Muttu", fielderName = "Madhu",
            ),
        ),
        allDeliveries = listOf(
            DeliveryUI(1, 1, 1, "4", runs = 4, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu"),
            DeliveryUI(1, 1, 2, "W", runs = 0, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu"),
        ),
        matchSettings = settings,
    )

    private fun entity(updatedAt: Long, groupId: String? = null) = MatchEntity(
        id = "m1", team1Name = "Strikers", team2Name = "Chasers", jokerPlayerName = null,
        team1CaptainName = null, team2CaptainName = null,
        firstInningsRuns = 20, firstInningsWickets = 1, secondInningsRuns = 12, secondInningsWickets = 0,
        winnerTeam = "Strikers", winningMargin = "8 runs", matchDate = 1_000L,
        groupId = groupId, groupName = groupId?.let { "Sunday League" }, shortPitch = false,
        playerOfTheMatchId = null, playerOfTheMatchName = null, playerOfTheMatchTeam = null,
        playerOfTheMatchImpact = null, playerOfTheMatchSummary = null,
        matchSettingsJson = null, allDeliveriesJson = null, updatedAt = updatedAt,
    )

    private val reassign = MatchCorrection.ReassignDismissal(
        innings = 1, wicketNumber = 1, outBatterName = "Gokul",
        dismissalType = WicketType.BOWLED, bowlerName = "Muttu", fielderName = null,
    )

    @Test
    fun `preview reports the change and the version it was computed against`() = runTest {
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 500L)
        coEvery { matchRepository.getMatchWithStats("m1") } returns match()

        val (outcome, updatedAt) = repository.preview("m1", listOf(reassign))

        assertThat(updatedAt).isEqualTo(500L)
        assertThat(outcome).isInstanceOf(CorrectionOutcome.Applied::class.java)
        assertThat((outcome as CorrectionOutcome.Applied).diff.lines.map { it.text })
            .contains("Wicket 1: Kushal → Gokul")
    }

    @Test
    fun `preview writes nothing`() = runTest {
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 500L)
        coEvery { matchRepository.getMatchWithStats("m1") } returns match()

        repository.preview("m1", listOf(reassign))

        coVerify(exactly = 0) { matchRepository.replaceMatchGraph(any()) }
        coVerify(exactly = 0) { matchRepository.saveMatch(any(), any(), any()) }
    }

    @Test
    fun `commit writes the corrected match as the whole truth`() = runTest {
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 500L)
        coEvery { matchRepository.getMatchWithStats("m1") } returns match()
        val saved = slot<MatchHistory>()
        coEvery { matchRepository.replaceMatchGraph(capture(saved)) } returns Unit

        val result = repository.commit("m1", listOf(reassign), expectedUpdatedAt = 500L)

        assertThat(result).isInstanceOf(MatchCorrectionRepository.CommitResult.Committed::class.java)
        assertThat(saved.captured.id).isEqualTo("m1")
        assertThat(saved.captured.firstInningsBatting.single { it.name == "Gokul" }.isOut).isTrue()
        assertThat(saved.captured.firstInningsFallOfWickets.single().batsmanName).isEqualTo("Gokul")
    }

    @Test
    fun `a match changed since the preview is refused rather than corrected blindly`() = runTest {
        // The classic race: a cloud download lands between previewing and confirming.
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 900L)
        coEvery { matchRepository.getMatchWithStats("m1") } returns match()

        val result = repository.commit("m1", listOf(reassign), expectedUpdatedAt = 500L)

        val rejected = result as MatchCorrectionRepository.CommitResult.Rejected
        assertThat(rejected.errors.map { it.code }).containsExactly("MATCH_CHANGED")
        assertThat(rejected.errors.single().message).contains("sync from another device")
        coVerify(exactly = 0) { matchRepository.replaceMatchGraph(any()) }
    }

    @Test
    fun `a rejected correction writes nothing`() = runTest {
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 500L)
        coEvery { matchRepository.getMatchWithStats("m1") } returns match()

        // Gokul came in after the first wicket fell, so he can't be the one it took.
        val impossible = reassign.copy(outBatterName = "Ajith")
        val result = repository.commit("m1", listOf(impossible), expectedUpdatedAt = 500L)

        assertThat(result).isInstanceOf(MatchCorrectionRepository.CommitResult.Rejected::class.java)
        coVerify(exactly = 0) { matchRepository.replaceMatchGraph(any()) }
    }

    @Test
    fun `a missing match is refused, not crashed on`() = runTest {
        coEvery { matchDao.getById("gone") } returns null

        val result = repository.commit("gone", listOf(reassign), expectedUpdatedAt = 1L)

        assertThat((result as MatchCorrectionRepository.CommitResult.Rejected).errors.map { it.code })
            .containsExactly("MATCH_NOT_FOUND")
    }

    @Test
    fun `an ungrouped match is always editable`() = runTest {
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 1L, groupId = null)

        assertThat(repository.editability("m1"))
            .isEqualTo(MatchCorrectionRepository.Editability.Fine)
    }

    @Test
    fun `a group match this device doesn't own is not ours to correct`() = runTest {
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 1L, groupId = "g1")
        coEvery { groupRepository.getGroupById("g1") } returns
            GroupEntity(id = "g1", name = "Sunday League", isOwner = false)

        val editability = repository.editability("m1")

        assertThat(editability)
            .isEqualTo(MatchCorrectionRepository.Editability.NotOurs("Sunday League"))
    }

    @Test
    fun `a correction to someone else's group match is refused, not saved locally`() = runTest {
        // Written, it would be right on this phone, wrong everywhere else in the group, and then
        // replaced by the owner's copy at the next sync — leaving whoever made it sure they had
        // fixed something. Only the owner corrects.
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 500L, groupId = "g1")
        coEvery { matchRepository.getMatchWithStats("m1") } returns match(groupId = "g1")
        coEvery { groupRepository.getGroupById("g1") } returns
            GroupEntity(id = "g1", name = "Sunday League", isOwner = false)

        val result = repository.commit("m1", listOf(reassign), expectedUpdatedAt = 500L)

        val rejected = result as MatchCorrectionRepository.CommitResult.Rejected
        assertThat(rejected.errors.map { it.code }).containsExactly("NOT_GROUP_OWNER")
        assertThat(rejected.errors.single().message).contains("Sunday League")
        coVerify(exactly = 0) { matchRepository.replaceMatchGraph(any()) }
    }

    @Test
    fun `a group match this device owns commits normally`() = runTest {
        coEvery { matchDao.getById("m1") } returns entity(updatedAt = 500L, groupId = "g1")
        coEvery { matchRepository.getMatchWithStats("m1") } returns match(groupId = "g1")
        coEvery { groupRepository.getGroupById("g1") } returns
            GroupEntity(id = "g1", name = "Sunday League", isOwner = true)

        val result = repository.commit("m1", listOf(reassign), expectedUpdatedAt = 500L)

        assertThat(result).isInstanceOf(MatchCorrectionRepository.CommitResult.Committed::class.java)
        coVerify { matchRepository.replaceMatchGraph(any()) }
    }
}
