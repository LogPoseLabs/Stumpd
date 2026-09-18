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
class Migration20To21Test {

    private lateinit var dbFile: File
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        dbFile = File(ctx.cacheDir, "migration_20_21_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS group_members (groupId TEXT NOT NULL, playerId TEXT NOT NULL, PRIMARY KEY(groupId, playerId))"
                    )
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS group_unavailable_players (groupId TEXT NOT NULL, playerId TEXT NOT NULL, PRIMARY KEY(groupId, playerId))"
                    )

                    // g1 has two members, one of whom is legitimately unavailable.
                    database.execSQL("INSERT INTO group_members VALUES ('g1','p1')")
                    database.execSQL("INSERT INTO group_members VALUES ('g1','p2')")
                    database.execSQL("INSERT INTO group_unavailable_players VALUES ('g1','p1')")
                    // Orphans: p9 was removed from g1, and g2 has no members at all.
                    database.execSQL("INSERT INTO group_unavailable_players VALUES ('g1','p9')")
                    database.execSQL("INSERT INTO group_unavailable_players VALUES ('g2','p1')")
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
    fun `migration 20 to 21 drops unavailable rows for non-members and keeps real ones`() {
        val ctx = RuntimeEnvironment.getApplication()
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    error("Should open existing DB")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        MIGRATION_20_21.migrate(db)

        val remaining = mutableListOf<Pair<String, String>>()
        db.query("SELECT groupId, playerId FROM group_unavailable_players").use { c ->
            while (c.moveToNext()) {
                remaining.add(c.getString(0) to c.getString(1))
            }
        }
        assertThat(remaining).containsExactly("g1" to "p1")

        // The whole point: available can no longer be negative.
        db.query(
            """
            SELECT (SELECT COUNT(*) FROM group_members WHERE groupId='g1')
                 - (SELECT COUNT(*) FROM group_unavailable_players WHERE groupId='g1')
            """.trimIndent()
        ).use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }

        db.close()
        helper.close()
    }
}
