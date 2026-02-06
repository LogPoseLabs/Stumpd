package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.InProgressMatchEntity

@Dao
interface InProgressMatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(match: InProgressMatchEntity)
    
    @Query("SELECT * FROM in_progress_matches ORDER BY lastSavedAt DESC LIMIT 1")
    suspend fun getLatest(): InProgressMatchEntity?
    
    @Query("SELECT * FROM in_progress_matches WHERE matchId = :matchId LIMIT 1")
    suspend fun getById(matchId: String): InProgressMatchEntity?
    
    @Query("DELETE FROM in_progress_matches WHERE matchId = :matchId")
    suspend fun delete(matchId: String)
    
    @Query("DELETE FROM in_progress_matches")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM in_progress_matches")
    suspend fun count(): Int
}
