package com.oreki.stumpd.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * v18 schema copied from exported Room schema [com.oreki.stumpd.data.local.db.StumpdDb/18.json].
 */
@RunWith(RobolectricTestRunner::class)
class Migration18To19Test {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        dbFile = File(ctx.cacheDir, "migration_18_19_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL(V18_CREATE_IN_PROGRESS_MATCHES)
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
    fun `migration 18 to 19 adds deliveryHistoryJson and partnershipsStateJson`() {
        val ctx = RuntimeEnvironment.getApplication()
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    error("Should open existing DB")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        MIGRATION_18_19.migrate(db)

        db.query("PRAGMA table_info(in_progress_matches)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) {
                cols.add(c.getString(1))
            }
            assertThat(cols).contains("deliveryHistoryJson")
            assertThat(cols).contains("partnershipsStateJson")
        }
        db.close()
        helper.close()
    }

    companion object {
        // Room schema v18 — in_progress_matches only (MigrationTest uses full DB; we need one table).
        private val V18_CREATE_IN_PROGRESS_MATCHES = (
            "CREATE TABLE IF NOT EXISTS `in_progress_matches` (" +
                "`matchId` TEXT NOT NULL, `team1Name` TEXT NOT NULL, `team2Name` TEXT NOT NULL, " +
                "`jokerName` TEXT NOT NULL, `groupId` TEXT, `groupName` TEXT, `tossWinner` TEXT, " +
                "`tossChoice` TEXT, `matchSettingsJson` TEXT NOT NULL, `team1PlayerIds` TEXT NOT NULL, " +
                "`team2PlayerIds` TEXT NOT NULL, `team1PlayerNames` TEXT NOT NULL, " +
                "`team2PlayerNames` TEXT NOT NULL, `currentInnings` INTEGER NOT NULL, " +
                "`currentOver` INTEGER NOT NULL, `ballsInOver` INTEGER NOT NULL, `totalWickets` INTEGER NOT NULL, " +
                "`team1PlayersJson` TEXT NOT NULL, `team2PlayersJson` TEXT NOT NULL, " +
                "`strikerIndex` INTEGER, `nonStrikerIndex` INTEGER, `bowlerIndex` INTEGER, " +
                "`firstInningsRuns` INTEGER NOT NULL, `firstInningsWickets` INTEGER NOT NULL, " +
                "`firstInningsOvers` INTEGER NOT NULL, `firstInningsBalls` INTEGER NOT NULL, " +
                "`totalExtras` INTEGER NOT NULL, `calculatedTotalRuns` INTEGER NOT NULL, " +
                "`completedBattersInnings1Json` TEXT, `completedBattersInnings2Json` TEXT, " +
                "`completedBowlersInnings1Json` TEXT, `completedBowlersInnings2Json` TEXT, " +
                "`firstInningsBattingPlayersJson` TEXT, `firstInningsBowlingPlayersJson` TEXT, " +
                "`jokerOutInCurrentInnings` INTEGER NOT NULL, `jokerBallsBowledInnings1` INTEGER NOT NULL, " +
                "`jokerBallsBowledInnings2` INTEGER NOT NULL, `powerplayRunsInnings1` INTEGER NOT NULL, " +
                "`powerplayRunsInnings2` INTEGER NOT NULL, `powerplayDoublingDoneInnings1` INTEGER NOT NULL, " +
                "`powerplayDoublingDoneInnings2` INTEGER NOT NULL, `allDeliveriesJson` TEXT, " +
                "`lastSavedAt` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, PRIMARY KEY(`matchId`)" +
                ")"
            )
    }
}
