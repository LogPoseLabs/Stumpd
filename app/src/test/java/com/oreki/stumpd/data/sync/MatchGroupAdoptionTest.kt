package com.oreki.stumpd.data.sync

import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.data.util.GsonProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchGroupAdoptionTest {

    @Test
    fun remapMatchIntoGroup_swapsGroupAndPlayerIds() {
        val match = MatchHistory(
            id = "m1",
            team1Name = "A",
            team2Name = "B",
            firstInningsRuns = 10,
            firstInningsWickets = 1,
            secondInningsRuns = 11,
            secondInningsWickets = 2,
            winnerTeam = "A",
            winningMargin = "1 run",
            groupId = "old-group",
            groupName = "Old",
            firstInningsBatting = listOf(
                PlayerMatchStats(id = "foreign-id-1", name = "Alice", team = "A", role = "BAT"),
            ),
        )
        val idMap = mapOf("alice" to "group-player-1")
        val remapped = MatchGroupAdoption.remapMatchIntoGroup(
            match = match,
            destinationGroupId = "dest-group",
            destinationGroupName = "Hogwarts",
            playerIdByNormalizedName = idMap,
        )
        assertEquals("dest-group", remapped.groupId)
        assertEquals("Hogwarts", remapped.groupName)
        assertEquals("group-player-1", remapped.firstInningsBatting.single().id)
        assertEquals("m1", remapped.id)
    }

    @Test
    fun remapMatchForMerge_usesDestinationIdsAndNames() {
        val match = MatchHistory(
            id = "friend-match",
            team1Name = "A",
            team2Name = "B",
            firstInningsRuns = 10,
            firstInningsWickets = 1,
            secondInningsRuns = 11,
            secondInningsWickets = 2,
            winnerTeam = "A",
            winningMargin = "1 run",
            groupId = "friend-group",
            firstInningsBatting = listOf(
                PlayerMatchStats(
                    id = "foreign-1",
                    name = "Rahul",
                    team = "A",
                    role = "BAT",
                    bowlerName = "Amit",
                ),
            ),
        )
        val remapped = MatchGroupAdoption.remapMatchForMerge(
            match = match,
            destinationGroupId = "hogwarts",
            destinationGroupName = "Hogwarts",
            newMatchId = "new-id",
            destByNormalizedSourceName = mapOf(
                "rahul" to MergeDestPlayer("hp-1", "Rahul Sharma"),
                "amit" to MergeDestPlayer("hp-2", "Amit K"),
            ),
        )
        assertEquals("new-id", remapped.id)
        assertEquals("hogwarts", remapped.groupId)
        val batter = remapped.firstInningsBatting.single()
        assertEquals("hp-1", batter.id)
        assertEquals("Rahul Sharma", batter.name)
        assertEquals("Amit K", batter.bowlerName)
    }

    @Test
    fun collectSourcePlayers_includesDismissalNames() {
        val match = MatchHistory(
            id = "m1",
            team1Name = "A",
            team2Name = "B",
            firstInningsRuns = 1,
            firstInningsWickets = 1,
            secondInningsRuns = 1,
            secondInningsWickets = 0,
            winnerTeam = "A",
            winningMargin = "1 run",
            firstInningsBatting = listOf(
                PlayerMatchStats(
                    id = "p1",
                    name = "Batter",
                    team = "A",
                    role = "BAT",
                    bowlerName = "Bowler",
                    fielderName = "Fielder",
                ),
            ),
        )
        val names = MatchGroupAdoption.collectSourcePlayers(listOf(match)).map { it.normalizedName }.toSet()
        assertTrue(names.containsAll(setOf("batter", "bowler", "fielder")))
    }
}

class MatchMergeParserTest {

    @Test
    fun parseMatchesFromJson_assemblesStatsFromBackup() {
        val entity = MatchEntity(
            id = "m1",
            team1Name = "A",
            team2Name = "B",
            jokerPlayerName = null,
            team1CaptainName = null,
            team2CaptainName = null,
            firstInningsRuns = 20,
            firstInningsWickets = 1,
            secondInningsRuns = 15,
            secondInningsWickets = 2,
            winnerTeam = "A",
            winningMargin = "5 runs",
            matchDate = 1L,
            groupId = "g-friend",
            groupName = "Friend Group",
            shortPitch = false,
            playerOfTheMatchId = null,
            playerOfTheMatchName = null,
            playerOfTheMatchTeam = null,
            playerOfTheMatchImpact = null,
            playerOfTheMatchSummary = null,
            matchSettingsJson = null,
            allDeliveriesJson = null,
        )
        val stats = listOf(
            PlayerMatchStatsEntity(
                matchId = "m1",
                playerId = "fp1",
                name = "Alice",
                team = "A",
                role = "BAT",
                runs = 12,
                ballsFaced = 8,
                fours = 1,
                sixes = 0,
                wickets = 0,
                runsConceded = 0,
                oversBowled = 0.0,
                isOut = false,
                isJoker = false,
            ),
        )
        val backup = MatchMergeParser.BackupFile(matches = listOf(entity), matchStats = stats)
        val json = GsonProvider.get().toJson(backup)
        val parsed = MatchMergeParser.parseMatchesFromJson(json)
        assertEquals(1, parsed.size)
        assertEquals("Alice", parsed.single().firstInningsBatting.single().name)
        assertEquals("fp1", parsed.single().firstInningsBatting.single().id)
        assertEquals(12, parsed.single().firstInningsBatting.single().runs)
    }
}
