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
 * The impacts table gets a real identity, and rows orphaned by earlier deletes are swept.
 *
 * Both halves are repairs to data already on people's phones, so the interesting cases are the
 * broken ones: duplicate impact rows from repeated re-saves, and child rows whose match is long
 * gone but which still counted toward career totals.
 */
@RunWith(RobolectricTestRunner::class)
class Migration22To23Test {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        dbFile = File(ctx.cacheDir, "migration_22_23_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL("CREATE TABLE IF NOT EXISTS matches (id TEXT NOT NULL PRIMARY KEY)")
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS player_impacts (
                            pk INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            matchId TEXT NOT NULL, playerId TEXT NOT NULL, name TEXT NOT NULL,
                            team TEXT NOT NULL, impact REAL NOT NULL, summary TEXT NOT NULL,
                            isJoker INTEGER NOT NULL, runs INTEGER NOT NULL, balls INTEGER NOT NULL,
                            dots INTEGER NOT NULL, singles INTEGER NOT NULL, twos INTEGER NOT NULL,
                            threes INTEGER NOT NULL, fours INTEGER NOT NULL, sixes INTEGER NOT NULL,
                            wickets INTEGER NOT NULL, runsConceded INTEGER NOT NULL,
                            oversBowled REAL NOT NULL
                        )
                        """.trimIndent()
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS player_match_stats (
                            matchId TEXT NOT NULL, playerId TEXT NOT NULL, team TEXT NOT NULL,
                            role TEXT NOT NULL, runs INTEGER NOT NULL,
                            PRIMARY KEY(matchId, playerId, team, role)
                        )
                        """.trimIndent()
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS partnerships (
                            matchId TEXT NOT NULL, innings INTEGER NOT NULL,
                            partnershipNumber INTEGER NOT NULL,
                            PRIMARY KEY(matchId, innings, partnershipNumber)
                        )
                        """.trimIndent()
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS fall_of_wickets (
                            matchId TEXT NOT NULL, innings INTEGER NOT NULL,
                            wicketNumber INTEGER NOT NULL,
                            PRIMARY KEY(matchId, innings, wicketNumber)
                        )
                        """.trimIndent()
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

    private fun insertImpact(db: SupportSQLiteDatabase, matchId: String, playerId: String, impact: Double) {
        db.execSQL(
            """
            INSERT INTO player_impacts (
                matchId, playerId, name, team, impact, summary, isJoker, runs, balls,
                dots, singles, twos, threes, fours, sixes, wickets, runsConceded, oversBowled
            ) VALUES ('$matchId', '$playerId', 'Kushal', 'Team A', $impact, 'ok', 0, 10, 8,
                      2, 3, 1, 0, 1, 0, 0, 0, 0.0)
            """.trimIndent()
        )
    }

    @Test
    fun `duplicate impact rows collapse to the most recently written one`() {
        val db = openHelper.writableDatabase
        db.execSQL("INSERT INTO matches (id) VALUES ('m1')")
        // What a re-save did before this migration: same player, three rows, newest last.
        insertImpact(db, "m1", "p1", impact = 10.0)
        insertImpact(db, "m1", "p1", impact = 20.0)
        insertImpact(db, "m1", "p1", impact = 30.0)
        insertImpact(db, "m1", "p2", impact = 5.0)

        MIGRATION_22_23.migrate(db)

        db.query("SELECT playerId, impact FROM player_impacts ORDER BY playerId").use { c ->
            assertThat(c.count).isEqualTo(2)
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("p1")
            assertThat(c.getDouble(1)).isEqualTo(30.0)
            c.moveToNext()
            assertThat(c.getString(0)).isEqualTo("p2")
            assertThat(c.getDouble(1)).isEqualTo(5.0)
        }
    }

    @Test
    fun `the new key makes a second insert for the same player replace rather than append`() {
        val db = openHelper.writableDatabase
        db.execSQL("INSERT INTO matches (id) VALUES ('m1')")
        insertImpact(db, "m1", "p1", impact = 10.0)

        MIGRATION_22_23.migrate(db)
        // Room inserts with REPLACE, so the same statement is what a re-save now runs.
        db.execSQL(
            """
            INSERT OR REPLACE INTO player_impacts (
                matchId, playerId, name, team, impact, summary, isJoker, runs, balls,
                dots, singles, twos, threes, fours, sixes, wickets, runsConceded, oversBowled
            ) VALUES ('m1', 'p1', 'Kushal', 'Team A', 42.0, 'ok', 0, 10, 8, 2, 3, 1, 0, 1, 0, 0, 0, 0.0)
            """.trimIndent()
        )

        db.query("SELECT impact FROM player_impacts").use { c ->
            assertThat(c.count).isEqualTo(1)
            c.moveToFirst()
            assertThat(c.getDouble(0)).isEqualTo(42.0)
        }
    }

    @Test
    fun `rows belonging to deleted matches are swept from all four child tables`() {
        val db = openHelper.writableDatabase
        db.execSQL("INSERT INTO matches (id) VALUES ('alive')")
        insertImpact(db, "alive", "p1", impact = 1.0)
        insertImpact(db, "gone", "p1", impact = 1.0)
        db.execSQL("INSERT INTO player_match_stats VALUES ('alive', 'p1', 'Team A', 'BAT', 20)")
        db.execSQL("INSERT INTO player_match_stats VALUES ('gone', 'p1', 'Team A', 'BAT', 99)")
        db.execSQL("INSERT INTO partnerships VALUES ('alive', 1, 1)")
        db.execSQL("INSERT INTO partnerships VALUES ('gone', 1, 1)")
        db.execSQL("INSERT INTO fall_of_wickets VALUES ('alive', 1, 1)")
        db.execSQL("INSERT INTO fall_of_wickets VALUES ('gone', 1, 1)")

        MIGRATION_22_23.migrate(db)

        fun matchIds(table: String): List<String> =
            db.query("SELECT matchId FROM $table").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }

        assertThat(matchIds("player_impacts")).containsExactly("alive")
        assertThat(matchIds("player_match_stats")).containsExactly("alive")
        assertThat(matchIds("partnerships")).containsExactly("alive")
        assertThat(matchIds("fall_of_wickets")).containsExactly("alive")
    }

    @Test
    fun `the indices survive the table rebuild`() {
        val db = openHelper.writableDatabase
        db.execSQL("INSERT INTO matches (id) VALUES ('m1')")
        insertImpact(db, "m1", "p1", impact = 1.0)

        MIGRATION_22_23.migrate(db)

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'player_impacts'")
            .use { c ->
                val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
                assertThat(names).contains("index_player_impacts_matchId")
                assertThat(names).contains("index_player_impacts_playerId")
            }
    }
}
