package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.PlayerEntity

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(players: List<PlayerEntity>)

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun get(id: String): PlayerEntity?

    @Query("SELECT * FROM players ORDER BY name")
    suspend fun list(): List<PlayerEntity>
    
    @Query("DELETE FROM players WHERE id = :id")
    suspend fun delete(id: String)
}
