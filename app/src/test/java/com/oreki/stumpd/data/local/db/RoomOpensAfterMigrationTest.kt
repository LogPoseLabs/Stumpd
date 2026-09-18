package com.oreki.stumpd.data.local.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * A migration has to produce what the entities declare — not merely run without error.
 *
 * The SQL-only migration tests pass whenever the statements execute, and that is not the same
 * thing. Room compares the migrated database against the entities — columns, primary keys **and
 * indices** — and rolls the whole migration back if anything disagrees. That is exactly how an
 * index created by a migration but not declared on `MatchEntity` took the app down on a real phone
 * with a real database: every statement ran, and Room refused the result.
 *
 * Room's own exported schema JSON is generated from the entities, so comparing the migrated
 * database against it is the same check Room performs, without needing its test helper or shipping
 * the schemas inside the APK.
 */
@RunWith(RobolectricTestRunner::class)
class RoomOpensAfterMigrationTest {

    private var openHelper: SupportSQLiteOpenHelper? = null
    private var dbFile: File? = null

    @After
    fun tearDown() {
        runCatching { openHelper?.close() }
        dbFile?.delete()
    }

    /** The exported schema for [version], as generated from the entities at build time. */
    private fun exportedSchema(version: Int): JSONObject {
        // Unit tests run with the module directory as the working directory.
        val file = File("schemas/com.oreki.stumpd.data.local.db.StumpdDb/$version.json")
        assertThat(file.exists()).isTrue()
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun entity(schema: JSONObject, table: String): JSONObject {
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            if (e.getString("tableName") == table) return e
        }
        error("No entity for $table in the exported schema")
    }

    private fun declaredColumns(schema: JSONObject, table: String): Set<String> {
        val fields = entity(schema, table).getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.toSet()
    }

    private fun declaredIndices(schema: JSONObject, table: String): Set<String> {
        val e = entity(schema, table)
        if (!e.has("indices")) return emptySet()
        val indices = e.getJSONArray("indices")
        return (0 until indices.length()).map { indices.getJSONObject(it).getString("name") }.toSet()
    }

    private fun actualColumns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun actualIndices(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name=?", arrayOf(table))
            .use { c ->
                buildSet {
                    while (c.moveToNext()) {
                        val name = c.getString(0)
                        // SQLite's own implicit indices are not Room's business.
                        if (!name.startsWith("sqlite_autoindex")) add(name)
                    }
                }
            }

    /** A version-25 database with just the two tables the tournament migration alters. */
    private fun openV25(): SupportSQLiteDatabase {
        val ctx = RuntimeEnvironment.getApplication()
        val file = File(ctx.cacheDir, "room_open_after_migration.db").also { it.delete() }
        dbFile = file
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(file.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS matches (
                            id TEXT NOT NULL PRIMARY KEY,
                            team1Name TEXT NOT NULL, team2Name TEXT NOT NULL,
                            jokerPlayerName TEXT, team1CaptainName TEXT, team2CaptainName TEXT,
                            firstInningsRuns INTEGER NOT NULL, firstInningsWickets INTEGER NOT NULL,
                            secondInningsRuns INTEGER NOT NULL, secondInningsWickets INTEGER NOT NULL,
                            winnerTeam TEXT NOT NULL, winningMargin TEXT NOT NULL,
                            matchDate INTEGER NOT NULL, groupId TEXT, groupName TEXT,
                            shortPitch INTEGER NOT NULL,
                            playerOfTheMatchId TEXT, playerOfTheMatchName TEXT,
                            playerOfTheMatchTeam TEXT, playerOfTheMatchImpact REAL,
                            playerOfTheMatchSummary TEXT,
                            matchSettingsJson TEXT, allDeliveriesJson TEXT,
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
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {}
            })
            .build()
        openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        return openHelper!!.writableDatabase
    }

    @Test
    fun `the tournament migration leaves matches exactly as the entity declares it`() {
        val db = openV25()
        val schema = exportedSchema(26)

        MIGRATION_25_26.migrate(db)

        // Columns. A missing one fails Room's validation on a real device.
        assertThat(actualColumns(db, "matches"))
            .containsExactlyElementsIn(declaredColumns(schema, "matches"))
        // The stats fixture above is deliberately minimal, so the useful direction is the other
        // one: every column the migration leaves behind must be declared on the entity. An
        // *undeclared* column is what Room rejects — and is how `teamId` broke the app once.
        assertThat(actualColumns(db, "player_match_stats")).contains("teamId")
        assertThat(declaredColumns(schema, "player_match_stats"))
            .containsAtLeastElementsIn(actualColumns(db, "player_match_stats"))
        assertThat(declaredColumns(schema, "matches"))
            .containsAtLeastElementsIn(actualColumns(db, "matches"))
    }

    @Test
    fun `and leaves matches with exactly the indices the entity declares`() {
        // The check the earlier tests lacked: an index created by the migration but not declared
        // on the entity rolls the whole migration back, and the app cannot open its database.
        val db = openV25()
        val schema = exportedSchema(26)

        MIGRATION_25_26.migrate(db)

        assertThat(actualIndices(db, "matches"))
            .containsExactlyElementsIn(declaredIndices(schema, "matches"))
    }

    @Test
    fun `the new tournament tables match their entities, columns and indices alike`() {
        val db = openV25()
        val schema = exportedSchema(26)

        MIGRATION_25_26.migrate(db)

        listOf(
            "tournaments",
            "tournament_teams",
            "tournament_squad_players",
            "tournament_fixtures",
        ).forEach { table ->
            assertThat(actualColumns(db, table))
                .containsExactlyElementsIn(declaredColumns(schema, table))
            assertThat(actualIndices(db, table))
                .containsExactlyElementsIn(declaredIndices(schema, table))
        }
    }

    /** A version-26 database with only the table the fixture-link migration alters. */
    private fun openV26InProgress(): SupportSQLiteDatabase {
        val ctx = RuntimeEnvironment.getApplication()
        val file = File(ctx.cacheDir, "room_open_after_migration_27.db").also { it.delete() }
        dbFile = file
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(file.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(26) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS in_progress_matches (
                            matchId TEXT NOT NULL PRIMARY KEY,
                            team1Name TEXT NOT NULL, team2Name TEXT NOT NULL,
                            jokerName TEXT NOT NULL,
                            lastSavedAt INTEGER NOT NULL, startedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {}
            })
            .build()
        openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        return openHelper!!.writableDatabase
    }

    @Test
    fun `the fixture-link migration adds only columns the entity declares`() {
        val db = openV26InProgress()
        val schema = exportedSchema(27)

        MIGRATION_26_27.migrate(db)

        val actual = actualColumns(db, "in_progress_matches")
        assertThat(actual).containsAtLeast(
            "tournamentId",
            "tournamentFixtureId",
            "team1Id",
            "team2Id",
        )
        // The fixture above is minimal, so the direction that matters is this one: an undeclared
        // column is what Room rejects, rolling the migration back and leaving the app unable to
        // open its database.
        assertThat(declaredColumns(schema, "in_progress_matches")).containsAtLeastElementsIn(actual)
        assertThat(actualIndices(db, "in_progress_matches"))
            .containsExactlyElementsIn(declaredIndices(schema, "in_progress_matches"))
    }

    @Test
    fun `a fresh database opens at the latest version and answers queries`() {
        // Proves the entities, the DAOs and the schema all agree — the other half of what Room
        // checks when it opens a database.
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.databaseBuilder(ctx, StumpdDb::class.java, "fresh_open_test.db").build()

        runBlocking {
            assertThat(db.tournamentDao().allTournaments()).isEmpty()
            assertThat(db.matchDao().list(null, 10)).isEmpty()
        }

        db.close()
        ctx.getDatabasePath("fresh_open_test.db").delete()
    }
}
