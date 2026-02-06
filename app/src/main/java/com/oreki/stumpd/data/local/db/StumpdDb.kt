package com.oreki.stumpd.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oreki.stumpd.data.local.dao.GroupDao
import com.oreki.stumpd.data.local.dao.InProgressMatchDao
import com.oreki.stumpd.data.local.dao.MatchDao
import com.oreki.stumpd.data.local.dao.PlayerDao
import com.oreki.stumpd.data.local.dao.TeamDao
import com.oreki.stumpd.data.local.dao.UserPreferencesDao
import com.oreki.stumpd.data.local.dao.PartnershipDao
import com.oreki.stumpd.data.local.dao.FallOfWicketDao
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

@Database(
    entities = [
        PlayerEntity::class, TeamEntity::class, TeamPlayerX::class,
        GroupEntity::class, GroupDefaultEntity::class,
        GroupMemberEntity::class, GroupUnavailablePlayerEntity::class, GroupLastTeamsEntity::class,
        MatchEntity::class, PlayerMatchStatsEntity::class, PlayerImpactEntity::class,
        InProgressMatchEntity::class,
        UserPreferencesEntity::class,
        PartnershipEntity::class, FallOfWicketEntity::class,
        JoinedGroupEntity::class
    ],
    version = 17,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class StumpdDb : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun teamDao(): TeamDao
    abstract fun matchDao(): MatchDao
    abstract fun groupDao(): GroupDao
    abstract fun inProgressMatchDao(): InProgressMatchDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun partnershipDao(): PartnershipDao
    abstract fun fallOfWicketDao(): FallOfWicketDao

    companion object {
        @Volatile private var INSTANCE: StumpdDb? = null

        fun get(context: Context): StumpdDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StumpdDb::class.java,
                    "stumpd.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .build().also { INSTANCE = it }
            }
    }
}

