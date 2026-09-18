package com.oreki.stumpd.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Tournaments arrive, and the long-dead `teams` tables leave.
 *
 * Everything added is nullable and outside every primary key, so the test that matters most is the
 * dull one: a match that existed before still reads back exactly as it was, simply not part of any
 * tournament.
 */
@RunWith(RobolectricTestRunner::class)
class Migration25To26Test {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        dbFile = File(ctx.cacheDir, "migration_25_26_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS matches (
                            id TEXT NOT NULL PRIMARY KEY,
                            team1Name TEXT NOT NULL, team2Name TEXT NOT NULL,
                            winnerTeam TEXT NOT NULL, winningMargin TEXT NOT NULL,
                            superOverWinner TEXT, superOversJson TEXT,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS player_match_stats (
                            matchId TEXT NOT NULL, playerId TEXT NOT NULL, team TEXT NOT NULL,
                            role TEXT NOT NULL, oversBowled REAL NOT NULL,
                            PRIMARY KEY(matchId, playerId, team, role)
                        )
                        """.trimIndent()
                    )
                    // The vestigial pair, keyed on the team's *name* — the thing tournaments have
                    // to escape.
                    database.execSQL("CREATE TABLE IF NOT EXISTS teams (name TEXT NOT NULL PRIMARY KEY)")
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS team_players (" +
                            "teamName TEXT NOT NULL, playerId TEXT NOT NULL, " +
                            "PRIMARY KEY(teamName, playerId))"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        openHelper.writableDatabase.close()
    }

    @After
    fun tearDown() {
        try {
            openHelper.close()
        } catch (_: Exception) {
        }
        if (dbFile.exists()) dbFile.delete()
    }

    private fun tables(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test
    fun `an existing match survives and belongs to no tournament`() {
        val db = openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO matches (id, team1Name, team2Name, winnerTeam, winningMargin, updatedAt) " +
                "VALUES ('m1', 'Strikers', 'Chasers', 'Strikers', '8 runs', 1000)"
        )
        db.execSQL(
            "INSERT INTO player_match_stats (matchId, playerId, team, role, oversBowled) " +
                "VALUES ('m1', 'p1', 'Chasers', 'BOWL', 1.3)"
        )

        MIGRATION_25_26.migrate(db)

        db.query(
            "SELECT winnerTeam, winningMargin, tournamentId, tournamentFixtureId, team1Id, team2Id " +
                "FROM matches"
        ).use { c ->
            assertThat(c.count).isEqualTo(1)
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("Strikers")
            assertThat(c.getString(1)).isEqualTo("8 runs")
            (2..5).forEach { assertThat(c.isNull(it)).isTrue() }
        }
        db.query("SELECT oversBowled, teamId FROM player_match_stats").use { c ->
            c.moveToFirst()
            assertThat(c.getDouble(0)).isWithin(0.001).of(1.3)
            assertThat(c.isNull(1)).isTrue()
        }
    }

    @Test
    fun `the four tournament tables are created`() {
        val db = openHelper.writableDatabase

        MIGRATION_25_26.migrate(db)

        assertThat(tables(db)).containsAtLeast(
            "tournaments",
            "tournament_teams",
            "tournament_squad_players",
            "tournament_fixtures",
        )
    }

    @Test
    fun `the dead name-keyed team tables are gone`() {
        val db = openHelper.writableDatabase
        assertThat(tables(db)).containsAtLeast("teams", "team_players")

        MIGRATION_25_26.migrate(db)

        assertThat(tables(db)).containsNoneOf("teams", "team_players")
    }

    @Test
    fun `a tournament and its parts can be written`() {
        val db = openHelper.writableDatabase
        MIGRATION_25_26.migrate(db)

        db.execSQL(
            """
            INSERT INTO tournaments (
                tournamentId, groupId, name, format, teamCount, squadSize, createdAt, updatedAt
            ) VALUES ('t1', 'g1', 'Sunday Cup', 'SINGLE_ROUND_ROBIN', 4, 5, 1, 1)
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO tournament_teams (teamId, tournamentId, name, seed, updatedAt) " +
                "VALUES ('t1:t1', 't1', 'Warriors', 1, 1)"
        )
        db.execSQL(
            "INSERT INTO tournament_squad_players (tournamentId, teamId, playerId, battingOrder) " +
                "VALUES ('t1', 't1:t1', 'p1', 1)"
        )
        db.execSQL(
            """
            INSERT INTO tournament_fixtures (
                fixtureId, tournamentId, stage, round, slot, leg, label, status, updatedAt
            ) VALUES ('t1:LEAGUE:1:0:1', 't1', 'LEAGUE', 1, 0, 1, 'Match 1', 'PENDING', 1)
            """.trimIndent()
        )

        db.query("SELECT name, status FROM tournaments WHERE tournamentId='t1'").use { c ->
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("Sunday Cup")
            // Defaulted by the schema, not by the insert.
            assertThat(c.getString(1)).isEqualTo("DRAFT")
        }
        db.query("SELECT COUNT(*) FROM tournament_fixtures WHERE tournamentId='t1'").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    @Test
    fun `two teams cannot share a seed within one tournament`() {
        val db = openHelper.writableDatabase
        MIGRATION_25_26.migrate(db)
        db.execSQL(
            "INSERT INTO tournament_teams (teamId, tournamentId, name, seed, updatedAt) " +
                "VALUES ('t1:t1', 't1', 'Warriors', 1, 1)"
        )

        val clash = runCatching {
            db.execSQL(
                "INSERT INTO tournament_teams (teamId, tournamentId, name, seed, updatedAt) " +
                    "VALUES ('t1:tX', 't1', 'Strikers', 1, 1)"
            )
        }

        assertThat(clash.isFailure).isTrue()
    }

    @Test
    fun `running the migration on a database without the dead tables is safe`() {
        // A device that somehow never had them — the drops are conditional.
        val db = openHelper.writableDatabase
        db.execSQL("DROP TABLE teams")
        db.execSQL("DROP TABLE team_players")

        val result = runCatching { MIGRATION_25_26.migrate(db) }

        assertThat(result.isSuccess).isTrue()
    }
}
