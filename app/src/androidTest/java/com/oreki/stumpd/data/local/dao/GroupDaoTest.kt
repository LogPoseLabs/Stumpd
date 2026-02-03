package com.oreki.stumpd.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.GroupDefaultEntity
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.local.entity.GroupLastTeamsEntity
import com.oreki.stumpd.data.local.entity.GroupMemberEntity
import com.oreki.stumpd.data.local.entity.PlayerEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupDaoTest {

    private lateinit var database: StumpdDb
    private lateinit var groupDao: GroupDao
    private lateinit var playerDao: PlayerDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            StumpdDb::class.java
        ).build()
        groupDao = database.groupDao()
        playerDao = database.playerDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertGroup_insertsNewGroup() = runTest {
        // Given
        val group = GroupEntity(id = "group1", name = "Test Group")

        // When
        groupDao.upsertGroup(group)

        // Then
        val groups = groupDao.listGroups()
        assertEquals(1, groups.size)
        assertEquals("Test Group", groups[0].name)
    }

    @Test
    fun upsertGroup_updatesExistingGroup() = runTest {
        // Given
        val group1 = GroupEntity(id = "group1", name = "Original Name")
        groupDao.upsertGroup(group1)

        // When
        val group2 = GroupEntity(id = "group1", name = "Updated Name")
        groupDao.upsertGroup(group2)

        // Then
        val groups = groupDao.listGroups()
        assertEquals(1, groups.size)
        assertEquals("Updated Name", groups[0].name)
    }

    @Test
    fun listGroups_returnsGroupsSortedByName() = runTest {
        // Given
        groupDao.upsertGroup(GroupEntity(id = "g1", name = "Zebras"))
        groupDao.upsertGroup(GroupEntity(id = "g2", name = "Alphas"))
        groupDao.upsertGroup(GroupEntity(id = "g3", name = "Betas"))

        // When
        val groups = groupDao.listGroups()

        // Then
        assertEquals(3, groups.size)
        assertEquals("Alphas", groups[0].name)
        assertEquals("Betas", groups[1].name)
        assertEquals("Zebras", groups[2].name)
    }

    @Test
    fun listGroups_returnsEmptyListWhenNoGroups() = runTest {
        // When
        val groups = groupDao.listGroups()

        // Then
        assertTrue(groups.isEmpty())
    }

    @Test
    fun upsertDefaults_insertsNewDefaults() = runTest {
        // Given
        val defaults = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Test Ground",
            format = "WHITE_BALL",
            shortPitch = true,
            matchSettingsJson = "{}"
        )

        // When
        groupDao.upsertDefaults(defaults)

        // Then
        val result = groupDao.getDefaults("group1")
        assertNotNull(result)
        assertEquals("Test Ground", result?.groundName)
        assertTrue(result?.shortPitch ?: false)
    }

    @Test
    fun upsertDefaults_updatesExistingDefaults() = runTest {
        // Given
        val defaults1 = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Original Ground",
            format = "WHITE_BALL",
            shortPitch = true,
            matchSettingsJson = "{}"
        )
        groupDao.upsertDefaults(defaults1)

        // When
        val defaults2 = GroupDefaultEntity(
            groupId = "group1",
            groundName = "Updated Ground",
            format = "RED_BALL",
            shortPitch = false,
            matchSettingsJson = "{}"
        )
        groupDao.upsertDefaults(defaults2)

        // Then
        val result = groupDao.getDefaults("group1")
        assertEquals("Updated Ground", result?.groundName)
        assertEquals("RED_BALL", result?.format)
        assertFalse(result?.shortPitch ?: true)
    }

    @Test
    fun getDefaults_returnsNullWhenNotFound() = runTest {
        // When
        val result = groupDao.getDefaults("nonexistent")

        // Then
        assertNull(result)
    }

    @Test
    fun upsertMembers_insertsNewMembers() = runTest {
        // Given
        val members = listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p2"),
            GroupMemberEntity("group1", "p3")
        )

        // When
        groupDao.upsertMembers(members)

        // Then
        val memberIds = groupDao.memberIds("group1")
        assertEquals(3, memberIds.size)
        assertTrue(memberIds.containsAll(listOf("p1", "p2", "p3")))
    }

    @Test
    fun upsertMembers_replacesOnConflict() = runTest {
        // Given
        groupDao.upsertMembers(listOf(GroupMemberEntity("group1", "p1")))

        // When - insert same combination again
        groupDao.upsertMembers(listOf(GroupMemberEntity("group1", "p1")))

        // Then
        val memberIds = groupDao.memberIds("group1")
        assertEquals(1, memberIds.size)
    }

    @Test
    fun clearMembers_removesAllMembersFromGroup() = runTest {
        // Given
        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p2"),
            GroupMemberEntity("group2", "p3")
        ))

        // When
        groupDao.clearMembers("group1")

        // Then
        val group1Members = groupDao.memberIds("group1")
        val group2Members = groupDao.memberIds("group2")
        
        assertTrue(group1Members.isEmpty())
        assertEquals(1, group2Members.size)
    }

    @Test
    fun members_returnsPlayerEntitiesForGroup() = runTest {
        // Given
        val players = listOf(
            PlayerEntity(id = "p1", name = "Player 1", isJoker = false),
            PlayerEntity(id = "p2", name = "Player 2", isJoker = false),
            PlayerEntity(id = "p3", name = "Player 3", isJoker = false)
        )
        playerDao.upsert(players)

        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p3")
        ))

        // When
        val members = groupDao.members("group1")

        // Then
        assertEquals(2, members.size)
        assertTrue(members.any { it.name == "Player 1" })
        assertTrue(members.any { it.name == "Player 3" })
        assertFalse(members.any { it.name == "Player 2" })
    }

    @Test
    fun members_returnsSortedByName() = runTest {
        // Given
        val players = listOf(
            PlayerEntity(id = "p1", name = "Zebra", isJoker = false),
            PlayerEntity(id = "p2", name = "Alpha", isJoker = false),
            PlayerEntity(id = "p3", name = "Beta", isJoker = false)
        )
        playerDao.upsert(players)

        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p2"),
            GroupMemberEntity("group1", "p3")
        ))

        // When
        val members = groupDao.members("group1")

        // Then
        assertEquals("Alpha", members[0].name)
        assertEquals("Beta", members[1].name)
        assertEquals("Zebra", members[2].name)
    }

    @Test
    fun members_returnsEmptyListWhenNoMembers() = runTest {
        // When
        val members = groupDao.members("group1")

        // Then
        assertTrue(members.isEmpty())
    }

    @Test
    fun upsertLastTeams_insertsNewLastTeams() = runTest {
        // Given
        val lastTeams = GroupLastTeamsEntity(
            groupId = "group1",
            team1PlayerIdsJson = """["p1","p2"]""",
            team2PlayerIdsJson = """["p3","p4"]""",
            team1Name = "Team A",
            team2Name = "Team B"
        )

        // When
        groupDao.upsertLastTeams(lastTeams)

        // Then
        val result = groupDao.lastTeams("group1")
        assertNotNull(result)
        assertEquals("Team A", result?.team1Name)
        assertEquals("Team B", result?.team2Name)
    }

    @Test
    fun upsertLastTeams_updatesExistingLastTeams() = runTest {
        // Given
        val lastTeams1 = GroupLastTeamsEntity(
            groupId = "group1",
            team1PlayerIdsJson = """["p1","p2"]""",
            team2PlayerIdsJson = """["p3","p4"]""",
            team1Name = "Team A",
            team2Name = "Team B"
        )
        groupDao.upsertLastTeams(lastTeams1)

        // When
        val lastTeams2 = GroupLastTeamsEntity(
            groupId = "group1",
            team1PlayerIdsJson = """["p5","p6"]""",
            team2PlayerIdsJson = """["p7","p8"]""",
            team1Name = "Team X",
            team2Name = "Team Y"
        )
        groupDao.upsertLastTeams(lastTeams2)

        // Then
        val result = groupDao.lastTeams("group1")
        assertEquals("Team X", result?.team1Name)
        assertEquals("Team Y", result?.team2Name)
        assertEquals("""["p5","p6"]""", result?.team1PlayerIdsJson)
    }

    @Test
    fun lastTeams_returnsNullWhenNotFound() = runTest {
        // When
        val result = groupDao.lastTeams("nonexistent")

        // Then
        assertNull(result)
    }

    @Test
    fun memberCount_returnsCorrectCount() = runTest {
        // Given
        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p2"),
            GroupMemberEntity("group1", "p3"),
            GroupMemberEntity("group2", "p4")
        ))

        // When
        val count1 = groupDao.memberCount("group1")
        val count2 = groupDao.memberCount("group2")
        val count3 = groupDao.memberCount("nonexistent")

        // Then
        assertEquals(3, count1)
        assertEquals(1, count2)
        assertEquals(0, count3)
    }

    @Test
    fun memberIds_returnsPlayerIdsSorted() = runTest {
        // Given
        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p3"),
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p2")
        ))

        // When
        val memberIds = groupDao.memberIds("group1")

        // Then
        assertEquals(3, memberIds.size)
        assertEquals("p1", memberIds[0])
        assertEquals("p2", memberIds[1])
        assertEquals("p3", memberIds[2])
    }

    @Test
    fun memberIds_returnsEmptyListWhenNoMembers() = runTest {
        // When
        val memberIds = groupDao.memberIds("nonexistent")

        // Then
        assertTrue(memberIds.isEmpty())
    }

    @Test
    fun members_handlesDeletedPlayers() = runTest {
        // Given
        val player = PlayerEntity(id = "p1", name = "Player 1", isJoker = false)
        playerDao.upsert(listOf(player))
        
        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p2") // p2 doesn't exist
        ))

        // When
        val members = groupDao.members("group1")

        // Then
        // Should only return p1 since p2 doesn't exist in players table (JOIN behavior)
        assertEquals(1, members.size)
        assertEquals("Player 1", members[0].name)
    }

    @Test
    fun groupOperations_workAcrossMultipleGroups() = runTest {
        // Given
        groupDao.upsertGroup(GroupEntity(id = "g1", name = "Group 1"))
        groupDao.upsertGroup(GroupEntity(id = "g2", name = "Group 2"))

        groupDao.upsertDefaults(GroupDefaultEntity(
            groupId = "g1",
            groundName = "Ground 1",
            format = "WHITE_BALL",
            shortPitch = true,
            matchSettingsJson = null
        ))

        groupDao.upsertMembers(listOf(
            GroupMemberEntity("g1", "p1"),
            GroupMemberEntity("g1", "p2"),
            GroupMemberEntity("g2", "p3")
        ))

        // When
        val groups = groupDao.listGroups()
        val g1Defaults = groupDao.getDefaults("g1")
        val g2Defaults = groupDao.getDefaults("g2")
        val g1Count = groupDao.memberCount("g1")
        val g2Count = groupDao.memberCount("g2")

        // Then
        assertEquals(2, groups.size)
        assertNotNull(g1Defaults)
        assertNull(g2Defaults)
        assertEquals(2, g1Count)
        assertEquals(1, g2Count)
    }

    @Test
    fun upsertMembers_allowsSamePlayerInMultipleGroups() = runTest {
        // Given
        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group2", "p1")
        ))

        // When
        val group1Members = groupDao.memberIds("group1")
        val group2Members = groupDao.memberIds("group2")

        // Then
        assertTrue(group1Members.contains("p1"))
        assertTrue(group2Members.contains("p1"))
    }

    @Test
    fun clearMembers_doesNotAffectOtherGroups() = runTest {
        // Given
        groupDao.upsertMembers(listOf(
            GroupMemberEntity("group1", "p1"),
            GroupMemberEntity("group1", "p2"),
            GroupMemberEntity("group2", "p1"),
            GroupMemberEntity("group2", "p3")
        ))

        // When
        groupDao.clearMembers("group1")

        // Then
        val group1Members = groupDao.memberIds("group1")
        val group2Members = groupDao.memberIds("group2")
        
        assertTrue(group1Members.isEmpty())
        assertEquals(2, group2Members.size)
    }
}



