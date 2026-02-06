package com.oreki.stumpd.data.local.dao

import androidx.room.*
import com.oreki.stumpd.data.local.entity.PartnershipEntity

@Dao
interface PartnershipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartnerships(partnerships: List<PartnershipEntity>)
    
    @Query("SELECT * FROM partnerships WHERE matchId = :matchId AND innings = :innings ORDER BY partnershipNumber")
    suspend fun getPartnershipsForInnings(matchId: String, innings: Int): List<PartnershipEntity>
    
    @Query("SELECT * FROM partnerships WHERE matchId = :matchId ORDER BY innings, partnershipNumber")
    suspend fun getPartnershipsForMatch(matchId: String): List<PartnershipEntity>
    
    @Query("SELECT * FROM partnerships WHERE matchId IN (:matchIds)")
    suspend fun getPartnershipsForMatches(matchIds: List<String>): List<PartnershipEntity>
    
    @Query("SELECT * FROM partnerships")
    suspend fun getAllPartnerships(): List<PartnershipEntity>
    
    @Query("DELETE FROM partnerships WHERE matchId = :matchId")
    suspend fun deleteForMatch(matchId: String)
}
