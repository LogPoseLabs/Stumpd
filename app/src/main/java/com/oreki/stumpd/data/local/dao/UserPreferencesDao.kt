package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.UserPreferencesEntity

@Dao
interface UserPreferencesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: UserPreferencesEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prefs: List<UserPreferencesEntity>)
    
    @Query("SELECT value FROM user_preferences WHERE key = :key LIMIT 1")
    suspend fun getValue(key: String): String?
    
    @Query("SELECT * FROM user_preferences")
    suspend fun getAll(): List<UserPreferencesEntity>
    
    @Query("DELETE FROM user_preferences WHERE key = :key")
    suspend fun delete(key: String)
}
