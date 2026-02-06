package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.TeamEntity
import com.oreki.stumpd.data.local.entity.TeamPlayerX

@Dao
interface TeamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTeams(teams: List<TeamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinks(links: List<TeamPlayerX>)

    @Query("DELETE FROM team_players WHERE teamName = :teamName")
    suspend fun clearTeamPlayers(teamName: String)
}
