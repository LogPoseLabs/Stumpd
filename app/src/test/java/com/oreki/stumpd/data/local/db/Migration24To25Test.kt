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
 * The matches table gains room for a super over.
 *
 * Purely additive, which is the point worth proving: every match already on a phone must read back
 * exactly as before, with the two new columns null, because "no super over" is what null means.
 */
@RunWith(RobolectricTestRunner::class)
class Migration24To25Test {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        dbFile = File(ctx.cacheDir, "migration_24_25_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    // The pre-migration shape, only as wide as this test needs.
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS matches (
                            id TEXT NOT NULL PRIMARY KEY,
                            team1Name TEXT NOT NULL, team2Name TEXT NOT NULL,
                            firstInningsRuns INTEGER NOT NULL, secondInningsRuns INTEGER NOT NULL,
                            winnerTeam TEXT NOT NULL, winningMargin TEXT NOT NULL,
                            allDeliveriesJson TEXT, updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    // The live counterpart gets a column too, so a match interrupted mid-super-
                    // over can resume into it.
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS in_progress_matches (
                            matchId TEXT NOT NULL PRIMARY KEY,
                            currentInnings INTEGER NOT NULL,
                            partnershipsStateJson TEXT
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

    @Test
    fun `an existing tie survives with no super over recorded against it`() {
        val db = openHelper.writableDatabase
        db.execSQL(
            """
            INSERT INTO matches (
                id, team1Name, team2Name, firstInningsRuns, secondInningsRuns,
                winnerTeam, winningMargin, allDeliveriesJson, updatedAt
            ) VALUES ('m1', 'Strikers', 'Chasers', 24, 24, 'TIE', 'Scores level', '[]', 1000)
            """.trimIndent()
        )

        MIGRATION_24_25.migrate(db)

        db.query("SELECT winnerTeam, winningMargin, superOverWinner, superOversJson FROM matches")
            .use { c ->
                assertThat(c.count).isEqualTo(1)
                c.moveToFirst()
                // Untouched: the migration adds room, it doesn't reinterpret history.
                assertThat(c.getString(0)).isEqualTo("TIE")
                assertThat(c.getString(1)).isEqualTo("Scores level")
                assertThat(c.isNull(2)).isTrue()
                assertThat(c.isNull(3)).isTrue()
            }
    }

    @Test
    fun `a match interrupted mid-super-over gets somewhere to keep its state`() {
        val db = openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO in_progress_matches (matchId, currentInnings, partnershipsStateJson) " +
                "VALUES ('live', 2, '{}')"
        )

        MIGRATION_24_25.migrate(db)

        db.query("SELECT currentInnings, superOverStateJson FROM in_progress_matches").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(2)
            assertThat(c.isNull(1)).isTrue()
        }
    }

    @Test
    fun `the new columns accept a super-over result`() {
        val db = openHelper.writableDatabase
        MIGRATION_24_25.migrate(db)

        db.execSQL(
            """
            INSERT INTO matches (
                id, team1Name, team2Name, firstInningsRuns, secondInningsRuns,
                winnerTeam, winningMargin, allDeliveriesJson, superOverWinner, superOversJson,
                updatedAt
            ) VALUES ('m2', 'Strikers', 'Chasers', 24, 24, 'Chasers', 'Super Over', '[]',
                      'Chasers', '[{"inning":3}]', 2000)
            """.trimIndent()
        )

        db.query("SELECT superOverWinner, superOversJson FROM matches WHERE id = 'm2'").use { c ->
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("Chasers")
            assertThat(c.getString(1)).isEqualTo("""[{"inning":3}]""")
        }
    }
}
