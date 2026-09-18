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
class Migration19To20Test {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        dbFile = File(ctx.cacheDir, "migration_19_20_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL("CREATE TABLE IF NOT EXISTS matches (id TEXT NOT NULL PRIMARY KEY)")
                    database.execSQL("CREATE TABLE IF NOT EXISTS players (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, isJoker INTEGER NOT NULL)")
                    database.execSQL("CREATE TABLE IF NOT EXISTS groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
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
    fun `migration 19 to 20 adds updatedAt to matches players groups`() {
        val ctx = RuntimeEnvironment.getApplication()
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    error("Should open existing DB")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        MIGRATION_19_20.migrate(db)

        fun assertHasUpdatedAt(table: String) {
            db.query("PRAGMA table_info($table)").use { c ->
                val cols = mutableListOf<String>()
                while (c.moveToNext()) {
                    cols.add(c.getString(1))
                }
                assertThat(cols).contains("updatedAt")
            }
        }
        assertHasUpdatedAt("matches")
        assertHasUpdatedAt("players")
        assertHasUpdatedAt("groups")

        db.close()
        helper.close()
    }
}
