package com.oreki.stumpd.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.PlayerEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerDaoTest {

    private lateinit var database: StumpdDb
    private lateinit var playerDao: PlayerDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            StumpdDb::class.java
        ).build()
        playerDao = database.playerDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsert_insertsNewPlayer() = runTest {
        // Given
        val player = PlayerEntity(id = "p1", name = "John Doe", isJoker = false)

        // When
        playerDao.upsert(listOf(player))

        // Then
        val result = playerDao.get("p1")
        assertNotNull(result)
        assertEquals("John Doe", result?.name)
        assertFalse(result?.isJoker ?: true)
    }

    @Test
    fun upsert_updatesExistingPlayer() = runTest {
        // Given
        val player1 = PlayerEntity(id = "p1", name = "John Doe", isJoker = false)
        playerDao.upsert(listOf(player1))

        // When
        val player2 = PlayerEntity(id = "p1", name = "John Updated", isJoker = true)
        playerDao.upsert(listOf(player2))

        // Then
        val result = playerDao.get("p1")
        assertEquals("John Updated", result?.name)
        assertTrue(result?.isJoker ?: false)
    }

    @Test
    fun upsert_insertsMultiplePlayers() = runTest {
        // Given
        val players = listOf(
            PlayerEntity(id = "p1", name = "Player 1", isJoker = false),
            PlayerEntity(id = "p2", name = "Player 2", isJoker = false),
            PlayerEntity(id = "p3", name = "Joker", isJoker = true)
        )

        // When
        playerDao.upsert(players)

        // Then
        val allPlayers = playerDao.list()
        assertEquals(3, allPlayers.size)
    }

    @Test
    fun get_returnsNullForNonexistentPlayer() = runTest {
        // When
        val result = playerDao.get("nonexistent")

        // Then
        assertNull(result)
    }

    @Test
    fun get_returnsCorrectPlayer() = runTest {
        // Given
        val players = listOf(
            PlayerEntity(id = "p1", name = "Player 1", isJoker = false),
            PlayerEntity(id = "p2", name = "Player 2", isJoker = false)
        )
        playerDao.upsert(players)

        // When
        val result = playerDao.get("p2")

        // Then
        assertNotNull(result)
        assertEquals("Player 2", result?.name)
    }

    @Test
    fun list_returnsEmptyListWhenNoPlayers() = runTest {
        // When
        val result = playerDao.list()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun list_returnsAllPlayersSortedByName() = runTest {
        // Given
        val players = listOf(
            PlayerEntity(id = "p1", name = "Charlie", isJoker = false),
            PlayerEntity(id = "p2", name = "Alice", isJoker = false),
            PlayerEntity(id = "p3", name = "Bob", isJoker = true)
        )
        playerDao.upsert(players)

        // When
        val result = playerDao.list()

        // Then
        assertEquals(3, result.size)
        assertEquals("Alice", result[0].name)
        assertEquals("Bob", result[1].name)
        assertEquals("Charlie", result[2].name)
    }

    @Test
    fun list_handlesSpecialCharactersInNames() = runTest {
        // Given
        val players = listOf(
            PlayerEntity(id = "p1", name = "O'Brien", isJoker = false),
            PlayerEntity(id = "p2", name = "José", isJoker = false),
            PlayerEntity(id = "p3", name = "李明", isJoker = false)
        )
        playerDao.upsert(players)

        // When
        val result = playerDao.list()

        // Then
        assertEquals(3, result.size)
        assertTrue(result.any { it.name == "O'Brien" })
        assertTrue(result.any { it.name == "José" })
        assertTrue(result.any { it.name == "李明" })
    }

    @Test
    fun upsert_replacesPlayerOnConflict() = runTest {
        // Given
        val player1 = PlayerEntity(id = "p1", name = "Original", isJoker = false)
        playerDao.upsert(listOf(player1))

        // When - insert same ID with different data
        val player2 = PlayerEntity(id = "p1", name = "Replaced", isJoker = true)
        playerDao.upsert(listOf(player2))

        // Then - should have only one player with updated data
        val allPlayers = playerDao.list()
        assertEquals(1, allPlayers.size)
        assertEquals("Replaced", allPlayers[0].name)
        assertTrue(allPlayers[0].isJoker)
    }

    @Test
    fun upsert_handlesEmptyList() = runTest {
        // When
        playerDao.upsert(emptyList())

        // Then
        val result = playerDao.list()
        assertTrue(result.isEmpty())
    }

    @Test
    fun get_distinguishesJokerPlayers() = runTest {
        // Given
        val regularPlayer = PlayerEntity(id = "p1", name = "Regular", isJoker = false)
        val jokerPlayer = PlayerEntity(id = "p2", name = "Joker", isJoker = true)
        playerDao.upsert(listOf(regularPlayer, jokerPlayer))

        // When
        val regular = playerDao.get("p1")
        val joker = playerDao.get("p2")

        // Then
        assertFalse(regular?.isJoker ?: true)
        assertTrue(joker?.isJoker ?: false)
    }
}



