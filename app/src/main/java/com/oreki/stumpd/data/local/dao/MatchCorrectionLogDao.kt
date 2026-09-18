package com.oreki.stumpd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oreki.stumpd.data.local.entity.MatchCorrectionLogEntity

@Dao
interface MatchCorrectionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MatchCorrectionLogEntity)

    @Query("SELECT * FROM match_corrections WHERE matchId = :matchId ORDER BY appliedAt DESC")
    suspend fun forMatch(matchId: String): List<MatchCorrectionLogEntity>

    @Query("DELETE FROM match_corrections WHERE matchId = :matchId")
    suspend fun deleteForMatch(matchId: String)
}
