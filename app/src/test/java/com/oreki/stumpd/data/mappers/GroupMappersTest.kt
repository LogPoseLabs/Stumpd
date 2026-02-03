package com.oreki.stumpd.data.mappers

import com.google.gson.Gson
import com.oreki.stumpd.BallFormat
import com.oreki.stumpd.GroupDefaultSettings
import com.oreki.stumpd.MatchSettings
import com.oreki.stumpd.TossChoice
import com.oreki.stumpd.data.local.entity.GroupDefaultEntity
import com.oreki.stumpd.data.local.entity.GroupEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupMappersTest {

    private val gson = Gson()

    @Test
    fun `GroupEntity toDomain converts all fields correctly`() {
        // Given
        val groupEntity = GroupEntity(id = "group1", name = "Test Group")
        val matchSettings = MatchSettings(totalOvers = 10, maxPlayersPerTeam = 11, shortPitch = true)
        val defaultsEntity = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Test Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true,
            matchSettingsJson = gson.toJson(matchSettings)
        )
        val memberIds = listOf("p1", "p2", "p3")

        // When
        val domain = groupEntity.toDomain(defaultsEntity, memberIds)

        // Then
        assertEquals("group1", domain.id)
        assertEquals("Test Group", domain.name)
        assertEquals(memberIds, domain.playerIds)
        assertEquals("Test Ground", domain.defaults.groundName)
        assertEquals(BallFormat.WHITE_BALL.name, domain.defaults.format)
        assertTrue(domain.defaults.shortPitch)
        assertEquals(10, domain.defaults.matchSettings.totalOvers)
        assertTrue(domain.defaults.matchSettings.shortPitch)
    }

    @Test
    fun `GroupEntity toDomain handles null defaults`() {
        // Given
        val groupEntity = GroupEntity(id = "group1", name = "Test Group")
        val memberIds = listOf("p1", "p2")

        // When
        val domain = groupEntity.toDomain(defaults = null, memberIds = memberIds)

        // Then
        assertEquals("group1", domain.id)
        assertEquals("Test Group", domain.name)
        assertEquals(memberIds, domain.playerIds)
        assertEquals("", domain.defaults.groundName)
        assertEquals(BallFormat.WHITE_BALL.name, domain.defaults.format)
        assertFalse(domain.defaults.shortPitch)
        // Should use default MatchSettings
        assertEquals(5, domain.defaults.matchSettings.totalOvers) // Default value
    }

    @Test
    fun `GroupEntity toDomain handles empty member list`() {
        // Given
        val groupEntity = GroupEntity(id = "group1", name = "Test Group")

        // When
        val domain = groupEntity.toDomain()

        // Then
        assertTrue(domain.playerIds.isEmpty())
    }

    @Test
    fun `GroupEntity toDomain preserves shortPitch in match settings`() {
        // Given
        val groupEntity = GroupEntity(id = "group1", name = "Test Group")
        val matchSettings = MatchSettings(totalOvers = 10, shortPitch = false)
        val defaultsEntity = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Ground",
            format = BallFormat.RED_BALL.toString(),
            shortPitch = true, // Different from match settings
            matchSettingsJson = gson.toJson(matchSettings)
        )

        // When
        val domain = groupEntity.toDomain(defaultsEntity, emptyList())

        // Then
        // shortPitch should be overridden from defaults entity
        assertTrue(domain.defaults.shortPitch)
        assertTrue(domain.defaults.matchSettings.shortPitch)
    }

    @Test
    fun `GroupEntity toDomain handles invalid match settings JSON`() {
        // Given
        val groupEntity = GroupEntity(id = "group1", name = "Test Group")
        val defaultsEntity = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = false,
            matchSettingsJson = "invalid json"
        )

        // When
        val domain = groupEntity.toDomain(defaultsEntity, emptyList())

        // Then
        // Should fall back to default MatchSettings
        assertNotNull(domain.defaults.matchSettings)
        assertEquals(5, domain.defaults.matchSettings.totalOvers) // Default value
    }

    @Test
    fun `GroupEntity toDomain handles null match settings JSON`() {
        // Given
        val groupEntity = GroupEntity(id = "group1", name = "Test Group")
        val defaultsEntity = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true,
            matchSettingsJson = null
        )

        // When
        val domain = groupEntity.toDomain(defaultsEntity, emptyList())

        // Then
        // Should use default MatchSettings
        assertNotNull(domain.defaults.matchSettings)
        assertTrue(domain.defaults.matchSettings.shortPitch)
    }

    @Test
    fun `GroupEntity toDomain handles RED_BALL format`() {
        // Given
        val groupEntity = GroupEntity(id = "group1", name = "Test Group")
        val defaultsEntity = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Ground",
            format = BallFormat.RED_BALL.toString(),
            shortPitch = false,
            matchSettingsJson = null
        )

        // When
        val domain = groupEntity.toDomain(defaultsEntity, emptyList())

        // Then
        assertEquals(BallFormat.RED_BALL.name, domain.defaults.format)
    }

    @Test
    fun `GroupDefaultSettings toEntityWithId converts all fields correctly`() {
        // Given
        val matchSettings = MatchSettings(
            totalOvers = 20,
            maxPlayersPerTeam = 11,
            noballRuns = 2,
            shortPitch = false
        )
        val groupDefaults = GroupDefaultSettings(
            matchSettings = matchSettings,
            groundName = "Main Ground",
            format = BallFormat.RED_BALL.toString(),
            shortPitch = true // Different from match settings
        )
        val groupId = "group123"

        // When
        val entity = groupDefaults.toEntityWithId(groupId)

        // Then
        assertEquals(groupId, entity.groupId)
        assertEquals("Main Ground", entity.groundName)
        assertEquals(BallFormat.RED_BALL.toString(), entity.format)
        assertTrue(entity.shortPitch)
        
        // Verify match settings JSON
        val deserializedSettings = gson.fromJson(entity.matchSettingsJson, MatchSettings::class.java)
        assertEquals(20, deserializedSettings.totalOvers)
        assertEquals(11, deserializedSettings.maxPlayersPerTeam)
        assertEquals(2, deserializedSettings.noballRuns)
        // shortPitch should be overridden
        assertTrue(deserializedSettings.shortPitch)
    }

    @Test
    fun `GroupDefaultSettings toEntityWithId overrides shortPitch in match settings`() {
        // Given
        val matchSettings = MatchSettings(
            totalOvers = 10,
            shortPitch = false // Will be overridden
        )
        val groupDefaults = GroupDefaultSettings(
            matchSettings = matchSettings,
            groundName = "Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true // This should override
        )

        // When
        val entity = groupDefaults.toEntityWithId("group1")

        // Then
        assertTrue(entity.shortPitch)
        val deserializedSettings = gson.fromJson(entity.matchSettingsJson, MatchSettings::class.java)
        assertTrue(deserializedSettings.shortPitch)
    }

    @Test
    fun `GroupDefaultSettings toEntityWithId handles empty ground name`() {
        // Given
        val groupDefaults = GroupDefaultSettings(
            matchSettings = MatchSettings(),
            groundName = "",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = false
        )

        // When
        val entity = groupDefaults.toEntityWithId("group1")

        // Then
        assertEquals("", entity.groundName)
    }

    @Test
    fun `GroupDefaultSettings toEntityWithId handles all match settings fields`() {
        // Given
        val matchSettings = MatchSettings(
            totalOvers = 15,
            maxPlayersPerTeam = 11,
            allowSingleSideBatting = true,
            noballRuns = 1,
            byeRuns = 1,
            legByeRuns = 1,
            legSideWideRuns = 2,
            offSideWideRuns = 1,
            powerplayOvers = 4,
            maxOversPerBowler = 3,
            enforceFollowOn = false,
            duckworthLewisMethod = false,
            jokerCanBatAndBowl = true,
            jokerMaxOvers = 2,
            tossWinnerChoice = TossChoice.BOWL_FIRST,
            enableSuperOver = true,
            shortPitch = false
        )
        val groupDefaults = GroupDefaultSettings(
            matchSettings = matchSettings,
            groundName = "Stadium",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true
        )

        // When
        val entity = groupDefaults.toEntityWithId("group1")

        // Then
        val deserializedSettings = gson.fromJson(entity.matchSettingsJson, MatchSettings::class.java)
        assertEquals(15, deserializedSettings.totalOvers)
        assertEquals(11, deserializedSettings.maxPlayersPerTeam)
        assertTrue(deserializedSettings.allowSingleSideBatting)
        assertEquals(1, deserializedSettings.noballRuns)
        assertEquals(1, deserializedSettings.byeRuns)
        assertEquals(1, deserializedSettings.legByeRuns)
        assertEquals(2, deserializedSettings.legSideWideRuns)
        assertEquals(1, deserializedSettings.offSideWideRuns)
        assertEquals(4, deserializedSettings.powerplayOvers)
        assertEquals(3, deserializedSettings.maxOversPerBowler)
        assertFalse(deserializedSettings.enforceFollowOn)
        assertFalse(deserializedSettings.duckworthLewisMethod)
        assertTrue(deserializedSettings.jokerCanBatAndBowl)
        assertEquals(2, deserializedSettings.jokerMaxOvers)
        assertEquals(TossChoice.BOWL_FIRST, deserializedSettings.tossWinnerChoice)
        assertTrue(deserializedSettings.enableSuperOver)
        assertTrue(deserializedSettings.shortPitch)
    }

    @Test
    fun `round trip conversion preserves data`() {
        // Given
        val originalGroupEntity = GroupEntity(id = "group1", name = "Test Group")
        val originalMatchSettings = MatchSettings(
            totalOvers = 10,
            maxPlayersPerTeam = 8,
            shortPitch = false
        )
        val originalDefaults = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Test Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true,
            matchSettingsJson = gson.toJson(originalMatchSettings)
        )
        val memberIds = listOf("p1", "p2", "p3")

        // When - convert to domain and back
        val domain = originalGroupEntity.toDomain(originalDefaults, memberIds)
        val entityBack = domain.defaults.toEntityWithId(domain.id)

        // Then
        assertEquals(originalDefaults.groupId, entityBack.groupId)
        assertEquals(originalDefaults.groundName, entityBack.groundName)
        assertEquals(originalDefaults.format, entityBack.format)
        assertEquals(originalDefaults.shortPitch, entityBack.shortPitch)
        
        val settingsBack = gson.fromJson(entityBack.matchSettingsJson, MatchSettings::class.java)
        assertEquals(10, settingsBack.totalOvers)
        assertEquals(8, settingsBack.maxPlayersPerTeam)
        assertTrue(settingsBack.shortPitch) // Overridden to match defaults
    }

    @Test
    fun `GroupEntity toDomain handles special characters in names`() {
        // Given
        val groupEntity = GroupEntity(id = "g1", name = "Test's Group #1 (2025)")
        val defaultsEntity = GroupDefaultEntity(
            groupId = "g1",
            groundName = "O'Brien's Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = false,
            matchSettingsJson = null
        )

        // When
        val domain = groupEntity.toDomain(defaultsEntity, emptyList())

        // Then
        assertEquals("Test's Group #1 (2025)", domain.name)
        assertEquals("O'Brien's Ground", domain.defaults.groundName)
    }

    @Test
    fun `GroupDefaultSettings toEntityWithId handles special characters`() {
        // Given
        val groupDefaults = GroupDefaultSettings(
            matchSettings = MatchSettings(),
            groundName = "Ground #1 (Main)",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = false
        )

        // When
        val entity = groupDefaults.toEntityWithId("g1")

        // Then
        assertEquals("Ground #1 (Main)", entity.groundName)
    }
}

