package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.MatchEntity
import com.oreki.stumpd.data.local.entity.PlayerImpactEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(m: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(rows: List<PlayerMatchStatsEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchStats(rows: List<PlayerMatchStatsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImpacts(rows: List<PlayerImpactEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayerImpacts(rows: List<PlayerImpactEntity>)

    @Transaction
    suspend fun insertFullMatch(
        m: MatchEntity,
        stats: List<PlayerMatchStatsEntity>,
        impacts: List<PlayerImpactEntity>
    ) {
        insertMatch(m)
        if (stats.isNotEmpty()) insertStats(stats)
        if (impacts.isNotEmpty()) insertImpacts(impacts)
    }

    @Query("""
        SELECT * FROM matches 
        WHERE (:groupId IS NULL OR groupId = :groupId)
        ORDER BY matchDate DESC
        LIMIT :limit
    """)
    suspend fun list(groupId: String?, limit: Int = 500): List<MatchEntity>

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteMatch(matchId: String)

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MatchEntity?

    @Query("SELECT * FROM player_match_stats WHERE matchId = :matchId")
    suspend fun statsForMatch(matchId: String): List<PlayerMatchStatsEntity>
    
    @Query("SELECT * FROM player_match_stats WHERE matchId IN (:matchIds)")
    suspend fun getStatsForMatches(matchIds: List<String>): List<PlayerMatchStatsEntity>

    @Query("SELECT * FROM player_impacts WHERE matchId = :matchId ORDER BY impact DESC")
    suspend fun impactsForMatch(matchId: String): List<PlayerImpactEntity>
    
    @Query("SELECT * FROM player_impacts WHERE matchId IN (:matchIds) ORDER BY impact DESC")
    suspend fun getImpactsForMatches(matchIds: List<String>): List<PlayerImpactEntity>
    
    @Query("UPDATE player_match_stats SET name = :newName WHERE playerId = :playerId")
    suspend fun updatePlayerNameInStats(playerId: String, newName: String): Int

    @Update
    suspend fun update(match: MatchEntity)

    @Update
    suspend fun updateStat(stat: PlayerMatchStatsEntity)
}
