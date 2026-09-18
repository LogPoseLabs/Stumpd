package com.oreki.stumpd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oreki.stumpd.data.local.entity.SyncProgressEntity

@Dao
interface SyncProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SyncProgressEntity)

    @Query("SELECT * FROM sync_progress WHERE collection = :collection")
    suspend fun forCollection(collection: String): List<SyncProgressEntity>

    @Query("SELECT COUNT(*) FROM sync_progress WHERE collection = :collection")
    suspend fun countFor(collection: String): Int

    @Query("DELETE FROM sync_progress WHERE collection = :collection AND recordId = :recordId")
    suspend fun clear(collection: String, recordId: String)

    @Query("DELETE FROM sync_progress")
    suspend fun clearAll()
}
