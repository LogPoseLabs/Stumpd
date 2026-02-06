package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.FallOfWicketEntity

@Dao
interface FallOfWicketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFallOfWickets(fows: List<FallOfWicketEntity>)
    
    @Query("SELECT * FROM fall_of_wickets WHERE matchId = :matchId AND innings = :innings ORDER BY wicketNumber")
    suspend fun getFallOfWicketsForInnings(matchId: String, innings: Int): List<FallOfWicketEntity>
    
    @Query("SELECT * FROM fall_of_wickets WHERE matchId = :matchId ORDER BY innings, wicketNumber")
    suspend fun getFallOfWicketsForMatch(matchId: String): List<FallOfWicketEntity>
    
    @Query("SELECT * FROM fall_of_wickets WHERE matchId IN (:matchIds)")
    suspend fun getFallOfWicketsForMatches(matchIds: List<String>): List<FallOfWicketEntity>
    
    @Query("SELECT * FROM fall_of_wickets")
    suspend fun getAllFallOfWickets(): List<FallOfWicketEntity>
    
    @Query("DELETE FROM fall_of_wickets WHERE matchId = :matchId")
    suspend fun deleteForMatch(matchId: String)
}
