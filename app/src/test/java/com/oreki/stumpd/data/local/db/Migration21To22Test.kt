package com.oreki.stumpd.data.local.db

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

@RunWith(RobolectricTestRunner::class)
class Migration21To22Test {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        dbFile = File(ctx.cacheDir, "migration_21_22_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL("CREATE TABLE IF NOT EXISTS matches (id TEXT NOT NULL PRIMARY KEY)")
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
    fun `migration 21 to 22 creates sync_progress keyed by collection and record`() {
        val ctx = RuntimeEnvironment.getApplication()
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    error("Should open existing DB")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        MIGRATION_21_22.migrate(db)

        db.execSQL("INSERT INTO sync_progress VALUES ('matches','m1',111)")
        // Same record id in a different collection must coexist.
        db.execSQL("INSERT INTO sync_progress VALUES ('players','m1',222)")
        // Re-recording progress for the same record replaces it rather than duplicating.
        db.execSQL("INSERT OR REPLACE INTO sync_progress VALUES ('matches','m1',333)")

        db.query("SELECT collection, recordId, syncedUpdatedAt FROM sync_progress ORDER BY collection").use { c ->
            val rows = mutableListOf<Triple<String, String, Long>>()
            while (c.moveToNext()) {
                rows.add(Triple(c.getString(0), c.getString(1), c.getLong(2)))
            }
            assertThat(rows).containsExactly(
                Triple("matches", "m1", 333L),
                Triple("players", "m1", 222L),
            )
        }

        db.close()
        helper.close()
    }
}
