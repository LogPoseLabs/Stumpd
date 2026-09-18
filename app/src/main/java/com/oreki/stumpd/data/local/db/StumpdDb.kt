package com.oreki.stumpd.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oreki.stumpd.data.local.dao.GroupDao
import com.oreki.stumpd.data.local.entity.TournamentFixtureEntity
import com.oreki.stumpd.data.local.entity.TournamentSquadPlayerEntity
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.local.dao.TournamentDao
import com.oreki.stumpd.data.local.dao.InProgressMatchDao
import com.oreki.stumpd.data.local.dao.MatchDao
import com.oreki.stumpd.data.local.dao.PlayerDao
import com.oreki.stumpd.data.local.dao.UserPreferencesDao
import com.oreki.stumpd.data.local.dao.PartnershipDao
import com.oreki.stumpd.data.local.dao.FallOfWicketDao
import com.oreki.stumpd.data.local.dao.MatchCorrectionLogDao
import com.oreki.stumpd.data.local.dao.SyncProgressDao
import com.oreki.stumpd.data.local.entity.*
// Add this migration constant in your StumpdDb class or a separate migrations file
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the new columns with default values
        database.execSQL(
            "ALTER TABLE group_last_teams ADD COLUMN team1Name TEXT NOT NULL DEFAULT 'Team A'"
        )
        database.execSQL(
            "ALTER TABLE group_last_teams ADD COLUMN team2Name TEXT NOT NULL DEFAULT 'Team B'"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create the in_progress_matches table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS in_progress_matches (
                matchId TEXT PRIMARY KEY NOT NULL,
                team1Name TEXT NOT NULL,
                team2Name TEXT NOT NULL,
                jokerName TEXT NOT NULL,
                groupId TEXT,
                groupName TEXT,
                tossWinner TEXT,
                tossChoice TEXT,
                matchSettingsJson TEXT NOT NULL,
                team1PlayerIds TEXT NOT NULL,
                team2PlayerIds TEXT NOT NULL,
                team1PlayerNames TEXT NOT NULL,
                team2PlayerNames TEXT NOT NULL,
                currentInnings INTEGER NOT NULL,
                currentOver INTEGER NOT NULL,
                ballsInOver INTEGER NOT NULL,
                totalWickets INTEGER NOT NULL,
                team1PlayersJson TEXT NOT NULL,
                team2PlayersJson TEXT NOT NULL,
                strikerIndex INTEGER,
                nonStrikerIndex INTEGER,
                bowlerIndex INTEGER,
                firstInningsRuns INTEGER NOT NULL,
                firstInningsWickets INTEGER NOT NULL,
                firstInningsOvers INTEGER NOT NULL,
                firstInningsBalls INTEGER NOT NULL,
                totalExtras INTEGER NOT NULL,
                calculatedTotalRuns INTEGER NOT NULL,
                completedBattersInnings1Json TEXT,
                completedBattersInnings2Json TEXT,
                completedBowlersInnings1Json TEXT,
                completedBowlersInnings2Json TEXT,
                firstInningsBattingPlayersJson TEXT,
                firstInningsBowlingPlayersJson TEXT,
                jokerOutInCurrentInnings INTEGER NOT NULL,
                jokerBallsBowledInnings1 INTEGER NOT NULL,
                jokerBallsBowledInnings2 INTEGER NOT NULL,
                lastSavedAt INTEGER NOT NULL,
                startedAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the allDeliveriesJson column to store ball-by-ball data for in-progress matches
        database.execSQL(
            "ALTER TABLE in_progress_matches ADD COLUMN allDeliveriesJson TEXT"
        )
        // Add the allDeliveriesJson column to store ball-by-ball data for completed matches
        database.execSQL(
            "ALTER TABLE matches ADD COLUMN allDeliveriesJson TEXT"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add fielding contribution columns
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN catches INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN runOuts INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN stumpings INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add dismissal information columns
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN dismissalType TEXT"
        )
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN bowlerName TEXT"
        )
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN fielderName TEXT"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add captain information columns to matches table
        database.execSQL(
            "ALTER TABLE matches ADD COLUMN team1CaptainName TEXT"
        )
        database.execSQL(
            "ALTER TABLE matches ADD COLUMN team2CaptainName TEXT"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add powerplay tracking columns to in_progress_matches table
        database.execSQL(
            "ALTER TABLE in_progress_matches ADD COLUMN powerplayRunsInnings1 INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE in_progress_matches ADD COLUMN powerplayRunsInnings2 INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE in_progress_matches ADD COLUMN powerplayDoublingDoneInnings1 INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE in_progress_matches ADD COLUMN powerplayDoublingDoneInnings2 INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create table for tracking unavailable players in groups
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS group_unavailable_players (
                groupId TEXT NOT NULL,
                playerId TEXT NOT NULL,
                PRIMARY KEY(groupId, playerId)
            )
        """.trimIndent())
        
        // Create index for efficient queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_group_unavailable_players_playerId 
            ON group_unavailable_players(playerId)
        """.trimIndent())
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create table for user preferences (replacing SharedPreferences)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS user_preferences (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
        """.trimIndent())
        
        // We could migrate existing SharedPreferences data here if needed
        // For now, users will just need to re-select their default group
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add run breakdown columns to player_match_stats table
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN dots INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN singles INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN twos INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_match_stats ADD COLUMN threes INTEGER NOT NULL DEFAULT 0"
        )
        
        // Add run breakdown columns to player_impacts table (correct table name)
        database.execSQL(
            "ALTER TABLE player_impacts ADD COLUMN dots INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_impacts ADD COLUMN singles INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_impacts ADD COLUMN twos INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE player_impacts ADD COLUMN threes INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create partnerships table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS partnerships (
                matchId TEXT NOT NULL,
                innings INTEGER NOT NULL,
                partnershipNumber INTEGER NOT NULL,
                batsman1Name TEXT NOT NULL,
                batsman2Name TEXT NOT NULL,
                runs INTEGER NOT NULL,
                balls INTEGER NOT NULL,
                batsman1Runs INTEGER NOT NULL,
                batsman2Runs INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                PRIMARY KEY(matchId, innings, partnershipNumber)
            )
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_partnerships_matchId 
            ON partnerships(matchId)
        """.trimIndent())
        
        // Create fall_of_wickets table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS fall_of_wickets (
                matchId TEXT NOT NULL,
                innings INTEGER NOT NULL,
                wicketNumber INTEGER NOT NULL,
                batsmanName TEXT NOT NULL,
                runs INTEGER NOT NULL,
                overs REAL NOT NULL,
                dismissalType TEXT,
                bowlerName TEXT,
                fielderName TEXT,
                PRIMARY KEY(matchId, innings, wicketNumber)
            )
        """.trimIndent())
        
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_fall_of_wickets_matchId 
            ON fall_of_wickets(matchId)
        """.trimIndent())
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add maidenOvers column to player_match_stats table
        database.execSQL("ALTER TABLE player_match_stats ADD COLUMN maidenOvers INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isRetired column to player_match_stats table
        database.execSQL("ALTER TABLE player_match_stats ADD COLUMN isRetired INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add invite code columns to groups table
        database.execSQL("ALTER TABLE groups ADD COLUMN inviteCode TEXT")
        database.execSQL("ALTER TABLE groups ADD COLUMN isOwner INTEGER NOT NULL DEFAULT 1")
        
        // Create joined_groups table for tracking groups joined via invite codes
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS joined_groups (
                groupId TEXT PRIMARY KEY NOT NULL,
                inviteCode TEXT NOT NULL,
                groupName TEXT NOT NULL,
                joinedAt INTEGER NOT NULL
            )
        """.trimIndent())
        
        // Create index for invite code lookups
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_joined_groups_inviteCode 
            ON joined_groups(inviteCode)
        """.trimIndent())
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add claimCode column for ownership recovery
        database.execSQL("ALTER TABLE groups ADD COLUMN claimCode TEXT")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add batting/bowling position columns to preserve order
        database.execSQL("ALTER TABLE player_match_stats ADD COLUMN battingPosition INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE player_match_stats ADD COLUMN bowlingPosition INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Remodel: split merged player rows into separate BAT/BOWL rows.
        // New PK includes 'role' column ("BAT" or "BOWL").

        // 1. Create new table with role in PK
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS player_match_stats_new (
                matchId TEXT NOT NULL,
                playerId TEXT NOT NULL,
                name TEXT NOT NULL,
                team TEXT NOT NULL,
                role TEXT NOT NULL,
                runs INTEGER NOT NULL,
                ballsFaced INTEGER NOT NULL,
                dots INTEGER NOT NULL DEFAULT 0,
                singles INTEGER NOT NULL DEFAULT 0,
                twos INTEGER NOT NULL DEFAULT 0,
                threes INTEGER NOT NULL DEFAULT 0,
                fours INTEGER NOT NULL,
                sixes INTEGER NOT NULL,
                wickets INTEGER NOT NULL,
                runsConceded INTEGER NOT NULL,
                oversBowled REAL NOT NULL,
                maidenOvers INTEGER NOT NULL DEFAULT 0,
                isOut INTEGER NOT NULL,
                isRetired INTEGER NOT NULL DEFAULT 0,
                isJoker INTEGER NOT NULL,
                catches INTEGER NOT NULL DEFAULT 0,
                runOuts INTEGER NOT NULL DEFAULT 0,
                stumpings INTEGER NOT NULL DEFAULT 0,
                dismissalType TEXT,
                bowlerName TEXT,
                fielderName TEXT,
                battingPosition INTEGER NOT NULL DEFAULT 0,
                bowlingPosition INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(matchId, playerId, team, role)
            )
        """.trimIndent())

        // 2. Insert BAT rows for players with batting activity
        //    Zero out bowling fields, keep batting fields + dismissal info
        database.execSQL("""
            INSERT INTO player_match_stats_new (
                matchId, playerId, name, team, role,
                runs, ballsFaced, dots, singles, twos, threes, fours, sixes,
                wickets, runsConceded, oversBowled, maidenOvers,
                isOut, isRetired, isJoker,
                catches, runOuts, stumpings,
                dismissalType, bowlerName, fielderName,
                battingPosition, bowlingPosition
            )
            SELECT
                matchId, playerId, name, team, 'BAT',
                runs, ballsFaced, dots, singles, twos, threes, fours, sixes,
                0, 0, 0.0, 0,
                isOut, isRetired, isJoker,
                0, 0, 0,
                dismissalType, bowlerName, fielderName,
                battingPosition, 0
            FROM player_match_stats
            WHERE runs > 0 OR ballsFaced > 0 OR fours > 0 OR sixes > 0 OR isOut = 1 OR isRetired = 1
        """.trimIndent())

        // 3. Insert BOWL rows for players with bowling or fielding activity
        //    Zero out batting fields, keep bowling + fielding fields
        database.execSQL("""
            INSERT OR IGNORE INTO player_match_stats_new (
                matchId, playerId, name, team, role,
                runs, ballsFaced, dots, singles, twos, threes, fours, sixes,
                wickets, runsConceded, oversBowled, maidenOvers,
                isOut, isRetired, isJoker,
                catches, runOuts, stumpings,
                dismissalType, bowlerName, fielderName,
                battingPosition, bowlingPosition
            )
            SELECT
                matchId, playerId, name, team, 'BOWL',
                0, 0, 0, 0, 0, 0, 0, 0,
                wickets, runsConceded, oversBowled, maidenOvers,
                0, 0, isJoker,
                catches, runOuts, stumpings,
                NULL, NULL, NULL,
                0, bowlingPosition
            FROM player_match_stats
            WHERE wickets > 0 OR oversBowled > 0.0 OR runsConceded > 0
                OR catches > 0 OR runOuts > 0 OR stumpings > 0
        """.trimIndent())

        // 4. Fallback: rows with no batting AND no bowling/fielding activity → BAT row
        database.execSQL("""
            INSERT OR IGNORE INTO player_match_stats_new (
                matchId, playerId, name, team, role,
                runs, ballsFaced, dots, singles, twos, threes, fours, sixes,
                wickets, runsConceded, oversBowled, maidenOvers,
                isOut, isRetired, isJoker,
                catches, runOuts, stumpings,
                dismissalType, bowlerName, fielderName,
                battingPosition, bowlingPosition
            )
            SELECT
                matchId, playerId, name, team, 'BAT',
                runs, ballsFaced, dots, singles, twos, threes, fours, sixes,
                wickets, runsConceded, oversBowled, maidenOvers,
                isOut, isRetired, isJoker,
                catches, runOuts, stumpings,
                dismissalType, bowlerName, fielderName,
                battingPosition, bowlingPosition
            FROM player_match_stats
            WHERE NOT (runs > 0 OR ballsFaced > 0 OR fours > 0 OR sixes > 0 OR isOut = 1 OR isRetired = 1)
              AND NOT (wickets > 0 OR oversBowled > 0.0 OR runsConceded > 0
                       OR catches > 0 OR runOuts > 0 OR stumpings > 0)
        """.trimIndent())

        // 5. Drop old table and rename
        database.execSQL("DROP TABLE player_match_stats")
        database.execSQL("ALTER TABLE player_match_stats_new RENAME TO player_match_stats")

        // 6. Recreate indices
        database.execSQL("CREATE INDEX IF NOT EXISTS index_player_match_stats_matchId ON player_match_stats(matchId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_player_match_stats_playerId ON player_match_stats(playerId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_player_match_stats_team ON player_match_stats(team)")
    }
}


val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE in_progress_matches ADD COLUMN deliveryHistoryJson TEXT")
        database.execSQL("ALTER TABLE in_progress_matches ADD COLUMN partnershipsStateJson TEXT")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        database.execSQL("ALTER TABLE matches ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $now")
        database.execSQL("ALTER TABLE players ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $now")
        database.execSQL("ALTER TABLE groups ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $now")
    }
}

/**
 * Repairs group availability data corrupted by earlier bugs that removed group members
 * without clearing their group_unavailable_players rows. Those orphans made the Groups
 * screen show a negative "Available" count, and nothing else prunes historical rows.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            DELETE FROM group_unavailable_players
            WHERE NOT EXISTS (
                SELECT 1 FROM group_members m
                WHERE m.groupId = group_unavailable_players.groupId
                  AND m.playerId = group_unavailable_players.playerId
            )
            """.trimIndent()
        )
    }
}

/**
 * Adds per-record upload progress so a large backlog can be uploaded across several days
 * without re-sending what already made it to the cloud.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_progress (
                collection TEXT NOT NULL,
                recordId TEXT NOT NULL,
                syncedUpdatedAt INTEGER NOT NULL,
                PRIMARY KEY(collection, recordId)
            )
            """.trimIndent()
        )
    }
}

/**
 * Gives `player_impacts` a real identity — `(matchId, playerId)` — and sweeps rows orphaned by
 * earlier deletes.
 *
 * The table's key was a generated row id, so re-saving a match appended a second full set of
 * impact rows rather than replacing them; adopt, merge and every cloud download did exactly that,
 * and `ImpactListActivity` read them all. The de-duplicating copy keeps the **last** row written
 * for each pair, which is the most recently computed one.
 *
 * The orphan sweep is here because `deleteMatch` only ever deleted the `matches` row, and
 * `playerCareerSummaries()` / `squadSizes()` scan `player_match_stats` without joining `matches` —
 * so figures from deleted matches were still counting toward career totals.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS player_impacts_new (
                matchId TEXT NOT NULL,
                playerId TEXT NOT NULL,
                name TEXT NOT NULL,
                team TEXT NOT NULL,
                impact REAL NOT NULL,
                summary TEXT NOT NULL,
                isJoker INTEGER NOT NULL,
                runs INTEGER NOT NULL,
                balls INTEGER NOT NULL,
                dots INTEGER NOT NULL,
                singles INTEGER NOT NULL,
                twos INTEGER NOT NULL,
                threes INTEGER NOT NULL,
                fours INTEGER NOT NULL,
                sixes INTEGER NOT NULL,
                wickets INTEGER NOT NULL,
                runsConceded INTEGER NOT NULL,
                oversBowled REAL NOT NULL,
                PRIMARY KEY(matchId, playerId)
            )
            """.trimIndent()
        )
        // Ordered by the old row id so the newest duplicate is the one that survives the REPLACE.
        database.execSQL(
            """
            INSERT OR REPLACE INTO player_impacts_new (
                matchId, playerId, name, team, impact, summary, isJoker, runs, balls,
                dots, singles, twos, threes, fours, sixes, wickets, runsConceded, oversBowled
            )
            SELECT matchId, playerId, name, team, impact, summary, isJoker, runs, balls,
                   dots, singles, twos, threes, fours, sixes, wickets, runsConceded, oversBowled
            FROM player_impacts
            ORDER BY pk
            """.trimIndent()
        )
        database.execSQL("DROP TABLE player_impacts")
        database.execSQL("ALTER TABLE player_impacts_new RENAME TO player_impacts")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_player_impacts_matchId ON player_impacts (matchId)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_player_impacts_playerId ON player_impacts (playerId)"
        )

        for (table in listOf(
            "player_match_stats", "player_impacts", "partnerships", "fall_of_wickets",
        )) {
            database.execSQL(
                "DELETE FROM $table WHERE matchId NOT IN (SELECT id FROM matches)"
            )
        }
    }
}

/**
 * Adds the record of corrections applied to a match.
 *
 * Corrections sync silently to the rest of a group, so without this a member finds a changed
 * number and no explanation. Keeping the match as it was alongside the summary also makes a
 * correction reversible.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS match_corrections (
                matchId TEXT NOT NULL,
                appliedAt INTEGER NOT NULL,
                deviceLabel TEXT NOT NULL,
                summary TEXT NOT NULL,
                beforeJson TEXT,
                PRIMARY KEY(matchId, appliedAt)
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_match_corrections_matchId ON match_corrections (matchId)"
        )
    }
}

/**
 * Room for a super over: the winner, and a row per eliminator innings.
 *
 * Both nullable and additive, so every existing match reads back as "no super over" and no query
 * changes. The winner is a scalar rather than part of the blob because it is the load-bearing
 * field — `resolveMatchResult` takes it as an input, which is what stops a later correction
 * re-deriving a super-over win back into a tie — and because its non-nullness is the "a super over
 * was played" flag.
 *
 * The per-innings figures are a JSON list rather than a fixed set of columns because the scorer can
 * choose to play another super over when the first is also level, so there is no fixed number of
 * them.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE matches ADD COLUMN superOverWinner TEXT")
        database.execSQL("ALTER TABLE matches ADD COLUMN superOversJson TEXT")
        // The live counterpart: a match interrupted mid-eliminator has to resume into it.
        database.execSQL("ALTER TABLE in_progress_matches ADD COLUMN superOverStateJson TEXT")
    }
}


/**
 * Tournaments: persistent teams with fixed squads, a schedule, and a table.
 *
 * Everything new is additive. The four columns on `matches` and the one on `player_match_stats` are
 * nullable and outside every primary key, so existing matches read back exactly as before and every
 * existing query keeps working — a match simply isn't part of a tournament.
 *
 * It also drops `teams` and `team_players`, which have been in the schema unused since the app was
 * written: their primary key is the team *name*, which is the very thing a tournament has to escape
 * (a name is part of the primary key of `player_match_stats`, so a renamed team orphans its own
 * scorecard). Both were verified empty on every device before removal.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tournaments (
                tournamentId TEXT NOT NULL PRIMARY KEY,
                groupId TEXT NOT NULL,
                name TEXT NOT NULL,
                format TEXT NOT NULL,
                teamCount INTEGER NOT NULL,
                squadSize INTEGER NOT NULL,
                poolCount INTEGER NOT NULL DEFAULT 0,
                advancePerPool INTEGER NOT NULL DEFAULT 0,
                pointsWin INTEGER NOT NULL DEFAULT 2,
                pointsTie INTEGER NOT NULL DEFAULT 1,
                pointsLoss INTEGER NOT NULL DEFAULT 0,
                pointsNoResult INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL DEFAULT 'DRAFT',
                matchSettingsJson TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tournaments_groupId ON tournaments (groupId)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tournament_teams (
                teamId TEXT NOT NULL PRIMARY KEY,
                tournamentId TEXT NOT NULL,
                name TEXT NOT NULL,
                shortName TEXT,
                captainPlayerId TEXT,
                captainName TEXT,
                seed INTEGER NOT NULL,
                poolOrdinal INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tournament_teams_tournamentId ON tournament_teams (tournamentId)")
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_tournament_teams_tournamentId_seed " +
                "ON tournament_teams (tournamentId, seed)"
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tournament_squad_players (
                tournamentId TEXT NOT NULL,
                teamId TEXT NOT NULL,
                playerId TEXT NOT NULL,
                battingOrder INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(teamId, playerId)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tournament_squad_players_tournamentId ON tournament_squad_players (tournamentId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tournament_squad_players_playerId ON tournament_squad_players (playerId)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tournament_fixtures (
                fixtureId TEXT NOT NULL PRIMARY KEY,
                tournamentId TEXT NOT NULL,
                stage TEXT NOT NULL,
                poolOrdinal INTEGER NOT NULL DEFAULT 0,
                round INTEGER NOT NULL,
                slot INTEGER NOT NULL,
                leg INTEGER NOT NULL DEFAULT 1,
                homeTeamId TEXT,
                awayTeamId TEXT,
                homeSourceRef TEXT,
                awaySourceRef TEXT,
                label TEXT NOT NULL,
                status TEXT NOT NULL,
                matchId TEXT,
                winnerTeamId TEXT,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tournament_fixtures_tournamentId ON tournament_fixtures (tournamentId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tournament_fixtures_matchId ON tournament_fixtures (matchId)")

        // A match can belong to a fixture. Nullable, and not in any key.
        database.execSQL("ALTER TABLE matches ADD COLUMN tournamentId TEXT")
        database.execSQL("ALTER TABLE matches ADD COLUMN tournamentFixtureId TEXT")
        database.execSQL("ALTER TABLE matches ADD COLUMN team1Id TEXT")
        database.execSQL("ALTER TABLE matches ADD COLUMN team2Id TEXT")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_matches_tournamentId ON matches (tournamentId)")

        // Written but not yet read: the first step off name-keyed team identity, without
        // backfilling 265 matches or touching a primary key.
        database.execSQL("ALTER TABLE player_match_stats ADD COLUMN teamId TEXT")

        database.execSQL("DROP TABLE IF EXISTS team_players")
        database.execSQL("DROP TABLE IF EXISTS teams")
    }
}

/**
 * A match in flight remembers the fixture it is settling.
 *
 * Without this, a process death between the first ball and the last would orphan the fixture: the
 * match would save as an ordinary one and the table would never move.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE in_progress_matches ADD COLUMN tournamentId TEXT")
        database.execSQL("ALTER TABLE in_progress_matches ADD COLUMN tournamentFixtureId TEXT")
        database.execSQL("ALTER TABLE in_progress_matches ADD COLUMN team1Id TEXT")
        database.execSQL("ALTER TABLE in_progress_matches ADD COLUMN team2Id TEXT")
    }
}

@Database(
    entities = [
        PlayerEntity::class,
        GroupEntity::class, GroupDefaultEntity::class,
        GroupMemberEntity::class, GroupUnavailablePlayerEntity::class, GroupLastTeamsEntity::class,
        MatchEntity::class, PlayerMatchStatsEntity::class, PlayerImpactEntity::class,
        InProgressMatchEntity::class,
        UserPreferencesEntity::class,
        PartnershipEntity::class, FallOfWicketEntity::class,
        JoinedGroupEntity::class,
        SyncProgressEntity::class,
        MatchCorrectionLogEntity::class,
        TournamentEntity::class, TournamentTeamEntity::class,
        TournamentSquadPlayerEntity::class, TournamentFixtureEntity::class
    ],
    version = 27,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class StumpdDb : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun groupDao(): GroupDao
    abstract fun inProgressMatchDao(): InProgressMatchDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun partnershipDao(): PartnershipDao
    abstract fun fallOfWicketDao(): FallOfWicketDao
    abstract fun syncProgressDao(): SyncProgressDao
    abstract fun matchCorrectionLogDao(): MatchCorrectionLogDao
    abstract fun tournamentDao(): TournamentDao

    companion object {
        @Volatile private var INSTANCE: StumpdDb? = null

        fun get(context: Context): StumpdDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StumpdDb::class.java,
                    "stumpd.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
                    .build().also { INSTANCE = it }
            }
    }
}

