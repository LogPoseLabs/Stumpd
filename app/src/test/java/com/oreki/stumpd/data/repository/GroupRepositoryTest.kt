package com.oreki.stumpd.data.repository

import com.google.gson.Gson
import com.oreki.stumpd.*
import com.oreki.stumpd.data.local.dao.GroupDao
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.GroupDefaultEntity
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.local.entity.GroupLastTeamsEntity
import com.oreki.stumpd.data.local.entity.GroupMemberEntity
import com.oreki.stumpd.data.local.entity.PlayerEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupRepositoryTest {

    private lateinit var db: StumpdDb
    private lateinit var groupDao: GroupDao
    private lateinit var repository: GroupRepository
    private val gson = Gson()

    @Before
    fun setup() {
        db = mockk(relaxed = true)
        groupDao = mockk(relaxed = true)

        every { db.groupDao() } returns groupDao

        repository = GroupRepository(db)
    }

    @Test
    fun `listGroups returns all groups from database`() = runTest {
        // Given
        val groups = listOf(
            GroupEntity(id = "group1", name = "Group 1"),
            GroupEntity(id = "group2", name = "Group 2"),
            GroupEntity(id = "group3", name = "Group 3")
        )
        coEvery { groupDao.listGroups() } returns groups

        // When
        val result = repository.listGroups()

        // Then
        assertEquals(groups, result)
        coVerify { groupDao.listGroups() }
    }

    @Test
    fun `listGroups returns empty list when no groups exist`() = runTest {
        // Given
        coEvery { groupDao.listGroups() } returns emptyList()

        // When
        val result = repository.listGroups()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `createGroup creates new group with defaults and returns ID`() = runTest {
        // Given
        val groupName = "Test Group"
        val matchSettings = MatchSettings(
            totalOvers = 10,
            maxPlayersPerTeam = 11,
            shortPitch = true
        )
        val defaults = GroupDefaultSettings(
            matchSettings = matchSettings,
            groundName = "Test Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true
        )

        // When
        val result = repository.createGroup(groupName, defaults)

        // Then
        assertNotNull(result)
        coVerify {
            groupDao.upsertGroup(withArg {
                assertEquals(groupName, it.name)
                assertEquals(result, it.id)
            })
        }
        coVerify {
            groupDao.upsertDefaults(withArg {
                assertEquals(result, it.groupId)
                assertEquals("Test Ground", it.groundName)
                assertEquals(BallFormat.WHITE_BALL.toString(), it.format)
                assertTrue(it.shortPitch)
                assertNotNull(it.matchSettingsJson)
            })
        }
    }

    @Test
    fun `createGroup trims group name`() = runTest {
        // Given
        val groupName = "  Test Group  "
        val defaults = GroupDefaultSettings(
            matchSettings = MatchSettings(),
            groundName = "Ground"
        )

        // When
        repository.createGroup(groupName, defaults)

        // Then
        coVerify {
            groupDao.upsertGroup(withArg {
                assertEquals("Test Group", it.name)
            })
        }
    }

    @Test
    fun `renameGroup updates group name`() = runTest {
        // Given
        val groupId = "group1"
        val newName = "Updated Group Name"

        // When
        repository.renameGroup(groupId, newName)

        // Then
        coVerify {
            groupDao.upsertGroup(GroupEntity(id = groupId, name = newName))
        }
    }

    @Test
    fun `renameGroup trims new name`() = runTest {
        // Given
        val groupId = "group1"
        val newName = "  Updated Name  "

        // When
        repository.renameGroup(groupId, newName)

        // Then
        coVerify {
            groupDao.upsertGroup(GroupEntity(id = groupId, name = "Updated Name"))
        }
    }

    @Test
    fun `deleteGroup clears members`() = runTest {
        // Given
        val groupId = "group1"

        // When
        repository.deleteGroup(groupId)

        // Then
        coVerify { groupDao.clearMembers(groupId) }
    }

    @Test
    fun `replaceMembers clears old members and adds new ones`() = runTest {
        // Given
        val groupId = "group1"
        val playerIds = listOf("p1", "p2", "p3")

        // When
        repository.replaceMembers(groupId, playerIds)

        // Then
        coVerify { groupDao.clearMembers(groupId) }
        coVerify {
            groupDao.upsertMembers(withArg { members ->
                assertEquals(3, members.size)
                assertEquals("p1", members[0].playerId)
                assertEquals("p2", members[1].playerId)
                assertEquals("p3", members[2].playerId)
                assertTrue(members.all { it.groupId == groupId })
            })
        }
    }

    @Test
    fun `replaceMembers removes duplicate player IDs`() = runTest {
        // Given
        val groupId = "group1"
        val playerIds = listOf("p1", "p2", "p1", "p3", "p2")

        // When
        repository.replaceMembers(groupId, playerIds)

        // Then
        coVerify {
            groupDao.upsertMembers(withArg { members ->
                assertEquals(3, members.size) // Only 3 unique players
                assertEquals(setOf("p1", "p2", "p3"), members.map { it.playerId }.toSet())
            })
        }
    }

    @Test
    fun `replaceMembers handles empty player list`() = runTest {
        // Given
        val groupId = "group1"
        val playerIds = emptyList<String>()

        // When
        repository.replaceMembers(groupId, playerIds)

        // Then
        coVerify { groupDao.clearMembers(groupId) }
        coVerify(exactly = 0) { groupDao.upsertMembers(any()) }
    }

    @Test
    fun `updateDefaults updates group defaults`() = runTest {
        // Given
        val groupId = "group1"
        val defaults = GroupDefaultEntity(
            groupId = "old_id", // Should be replaced
            groundName = "New Ground",
            format = BallFormat.RED_BALL.toString(),
            shortPitch = false,
            matchSettingsJson = gson.toJson(MatchSettings())
        )

        // When
        repository.updateDefaults(groupId, defaults)

        // Then
        coVerify {
            groupDao.upsertDefaults(withArg {
                assertEquals(groupId, it.groupId) // Should use provided groupId
                assertEquals("New Ground", it.groundName)
                assertEquals(BallFormat.RED_BALL.toString(), it.format)
                assertFalse(it.shortPitch)
            })
        }
    }

    @Test
    fun `getMembers returns players for group`() = runTest {
        // Given
        val groupId = "group1"
        val players = listOf(
            PlayerEntity(id = "p1", name = "Player 1", isJoker = false),
            PlayerEntity(id = "p2", name = "Player 2", isJoker = false)
        )
        coEvery { groupDao.members(groupId) } returns players

        // When
        val result = repository.getMembers(groupId)

        // Then
        assertEquals(players, result)
        coVerify { groupDao.members(groupId) }
    }

    @Test
    fun `getMembers returns empty list when group has no members`() = runTest {
        // Given
        val groupId = "group1"
        coEvery { groupDao.members(groupId) } returns emptyList()

        // When
        val result = repository.getMembers(groupId)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `saveLastTeams serializes and saves team data`() = runTest {
        // Given
        val groupId = "group1"
        val team1Ids = listOf("p1", "p2", "p3")
        val team2Ids = listOf("p4", "p5", "p6")
        val team1Name = "Team A"
        val team2Name = "Team B"

        // When
        repository.saveLastTeams(groupId, team1Ids, team2Ids, team1Name, team2Name)

        // Then
        coVerify {
            groupDao.upsertLastTeams(withArg {
                assertEquals(groupId, it.groupId)
                assertEquals(team1Name, it.team1Name)
                assertEquals(team2Name, it.team2Name)
                
                // Verify JSON contains the player IDs
                val savedTeam1Ids: List<String> = gson.fromJson(
                    it.team1PlayerIdsJson,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java,
                        String::class.java
                    ).type
                )
                val savedTeam2Ids: List<String> = gson.fromJson(
                    it.team2PlayerIdsJson,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java,
                        String::class.java
                    ).type
                )
                
                assertEquals(team1Ids, savedTeam1Ids)
                assertEquals(team2Ids, savedTeam2Ids)
            })
        }
    }

    @Test
    fun `loadLastTeams returns saved team data`() = runTest {
        // Given
        val groupId = "group1"
        val team1Ids = listOf("p1", "p2", "p3")
        val team2Ids = listOf("p4", "p5", "p6")
        val entity = GroupLastTeamsEntity(
            groupId = groupId,
            team1PlayerIdsJson = gson.toJson(team1Ids),
            team2PlayerIdsJson = gson.toJson(team2Ids),
            team1Name = "Team A",
            team2Name = "Team B"
        )
        coEvery { groupDao.lastTeams(groupId) } returns entity

        // When
        val result = repository.loadLastTeams(groupId)

        // Then
        assertNotNull(result)
        assertEquals(team1Ids, result!!.first)
        assertEquals(team2Ids, result.second)
        assertEquals("Team A", result.third.first)
        assertEquals("Team B", result.third.second)
    }

    @Test
    fun `loadLastTeams returns null when no saved teams exist`() = runTest {
        // Given
        val groupId = "group1"
        coEvery { groupDao.lastTeams(groupId) } returns null

        // When
        val result = repository.loadLastTeams(groupId)

        // Then
        assertNull(result)
    }

    @Test
    fun `loadLastTeams returns null on JSON parse error`() = runTest {
        // Given
        val groupId = "group1"
        val entity = GroupLastTeamsEntity(
            groupId = groupId,
            team1PlayerIdsJson = "invalid json",
            team2PlayerIdsJson = "invalid json",
            team1Name = "Team A",
            team2Name = "Team B"
        )
        coEvery { groupDao.lastTeams(groupId) } returns entity

        // When
        val result = repository.loadLastTeams(groupId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getDefaults returns defaults for group`() = runTest {
        // Given
        val groupId = "group1"
        val defaults = GroupDefaultEntity(
            groupId = groupId,
            groundName = "Test Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true,
            matchSettingsJson = gson.toJson(MatchSettings())
        )
        coEvery { groupDao.getDefaults(groupId) } returns defaults

        // When
        val result = repository.getDefaults(groupId)

        // Then
        assertEquals(defaults, result)
    }

    @Test
    fun `getDefaults returns null when no defaults exist`() = runTest {
        // Given
        val groupId = "group1"
        coEvery { groupDao.getDefaults(groupId) } returns null

        // When
        val result = repository.getDefaults(groupId)

        // Then
        assertNull(result)
    }

    @Test
    fun `listGroupSummaries returns groups with defaults and member count`() = runTest {
        // Given
        val groups = listOf(
            GroupEntity(id = "group1", name = "Group 1"),
            GroupEntity(id = "group2", name = "Group 2")
        )
        val defaults1 = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Ground 1",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true,
            matchSettingsJson = null
        )

        coEvery { groupDao.listGroups() } returns groups
        coEvery { groupDao.getDefaults("group1") } returns defaults1
        coEvery { groupDao.getDefaults("group2") } returns null
        coEvery { groupDao.memberCount("group1") } returns 10
        coEvery { groupDao.memberCount("group2") } returns 5

        // When
        val result = repository.listGroupSummaries()

        // Then
        assertEquals(2, result.size)
        
        val summary1 = result[0]
        assertEquals("Group 1", summary1.first.name)
        assertEquals(defaults1, summary1.second)
        assertEquals(10, summary1.third)
        
        val summary2 = result[1]
        assertEquals("Group 2", summary2.first.name)
        assertNull(summary2.second)
        assertEquals(5, summary2.third)
    }

    @Test
    fun `listGroupSummaries returns empty list when no groups exist`() = runTest {
        // Given
        coEvery { groupDao.listGroups() } returns emptyList()

        // When
        val result = repository.listGroupSummaries()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getGroupForEdit returns group entity, defaults and member IDs`() = runTest {
        // Given
        val groupId = "group1"
        val groupEntity = GroupEntity(id = groupId, name = "Test Group")
        val defaults = GroupDefaultEntity(
            groupId = groupId,
            groundName = "Test Ground",
            format = BallFormat.WHITE_BALL.toString(),
            shortPitch = true,
            matchSettingsJson = null
        )
        val memberIds = listOf("p1", "p2", "p3")

        coEvery { groupDao.listGroups() } returns listOf(groupEntity)
        coEvery { groupDao.getDefaults(groupId) } returns defaults
        coEvery { groupDao.memberIds(groupId) } returns memberIds

        // When
        val result = repository.getGroupForEdit(groupId)

        // Then
        assertEquals(groupEntity, result.first)
        assertEquals(defaults, result.second)
        assertEquals(memberIds, result.third)
    }

    @Test
    fun `getGroupForEdit returns null defaults when not set`() = runTest {
        // Given
        val groupId = "group1"
        val groupEntity = GroupEntity(id = groupId, name = "Test Group")
        val memberIds = listOf("p1", "p2")

        coEvery { groupDao.listGroups() } returns listOf(groupEntity)
        coEvery { groupDao.getDefaults(groupId) } returns null
        coEvery { groupDao.memberIds(groupId) } returns memberIds

        // When
        val result = repository.getGroupForEdit(groupId)

        // Then
        assertEquals(groupEntity, result.first)
        assertNull(result.second)
        assertEquals(memberIds, result.third)
    }

    @Test
    fun `createGroup generates unique IDs for different groups`() = runTest {
        // Given
        val defaults = GroupDefaultSettings(
            matchSettings = MatchSettings(),
            groundName = "Ground"
        )

        // When
        val id1 = repository.createGroup("Group 1", defaults)
        val id2 = repository.createGroup("Group 2", defaults)

        // Then
        assertNotEquals(id1, id2)
    }

    @Test
    fun `saveLastTeams handles empty teams`() = runTest {
        // Given
        val groupId = "group1"
        val team1Ids = emptyList<String>()
        val team2Ids = emptyList<String>()

        // When
        repository.saveLastTeams(groupId, team1Ids, team2Ids, "Team A", "Team B")

        // Then
        coVerify {
            groupDao.upsertLastTeams(withArg {
                val savedTeam1: List<String> = gson.fromJson(
                    it.team1PlayerIdsJson,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java,
                        String::class.java
                    ).type
                )
                val savedTeam2: List<String> = gson.fromJson(
                    it.team2PlayerIdsJson,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java,
                        String::class.java
                    ).type
                )
                assertTrue(savedTeam1.isEmpty())
                assertTrue(savedTeam2.isEmpty())
            })
        }
    }
}

