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
    suspend fun insertImpacts(rows: List<PlayerImpactEntity>)

    @Query("DELETE FROM player_match_stats WHERE matchId = :matchId")
    suspend fun deleteStatsForMatch(matchId: String)

    @Query("DELETE FROM player_impacts WHERE matchId = :matchId")
    suspend fun deleteImpactsForMatch(matchId: String)

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

    /**
     * Writes a match's rows as *the* rows, clearing the previous ones first.
     *
     * [insertFullMatch] can only add or overwrite by key, so re-saving a changed match leaves
     * behind anything whose key moved — a bowler whose figures were reassigned to someone else
     * keeps a stale row, and the scorecard reads it back. Use this whenever the caller holds the
     * complete, authoritative graph; use [insertFullMatch] when merging a copy that might be
     * partial (a cloud download cut short by the sync quota, an import).
     */
    @Transaction
    suspend fun replaceFullMatch(
        m: MatchEntity,
        stats: List<PlayerMatchStatsEntity>,
        impacts: List<PlayerImpactEntity>
    ) {
        deleteStatsForMatch(m.id)
        deleteImpactsForMatch(m.id)
        insertFullMatch(m, stats, impacts)
    }

    @Query("""
        SELECT * FROM matches 
        WHERE (:groupId IS NULL OR groupId = :groupId)
        ORDER BY matchDate DESC
        LIMIT :limit
    """)
    suspend fun list(groupId: String?, limit: Int = 500): List<MatchEntity>

    @Query("""
        SELECT * FROM matches
        WHERE updatedAt > :updatedAfter
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun listUpdatedAfter(updatedAfter: Long, limit: Int): List<MatchEntity>

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteMatch(matchId: String)

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MatchEntity?

    /**
     * One career line per player, for list screens that need a summary without loading and
     * aggregating every match.
     *
     * Runs come from BAT rows and wickets from BOWL rows deliberately: many (player, match)
     * pairs store a full copy of the figures on both rows, so summing across roles would
     * double count.
     */
    @Query("""
        SELECT playerId AS playerId,
               COUNT(DISTINCT matchId) AS matches,
               SUM(CASE WHEN role = 'BAT' THEN runs ELSE 0 END) AS runs,
               SUM(CASE WHEN role = 'BOWL' THEN wickets ELSE 0 END) AS wickets
        FROM player_match_stats
        GROUP BY playerId
    """)
    suspend fun playerCareerSummaries(): List<PlayerCareerSummary>

    /**
     * How many players each side fielded in each match, for the wicket margin.
     *
     * "Won by N wickets" is measured against the size of the chasing side, and the history list
     * loads matches without their stats, so this is the cheap way to get the squad sizes for a
     * whole screen of matches at once.
     */
    @Query("""
        SELECT matchId AS matchId,
               team AS team,
               COUNT(DISTINCT playerId) AS squadSize
        FROM player_match_stats
        GROUP BY matchId, team
    """)
    suspend fun squadSizes(): List<MatchSquadSize>

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

    /**
     * The matches that still call a player something other than [name].
     *
     * `player_match_stats` is the one table carrying a real `playerId`; everywhere else inside a
     * match the player is a name, so this is the only way to find the matches a rename has to be
     * carried through. Asking for the ones that *disagree* rather than all of them means saving a
     * player without renaming them costs one query and no work — and that a rename is judged
     * against what the matches actually say, not against what the players table used to say.
     */
    @Query(
        "SELECT DISTINCT matchId FROM player_match_stats " +
            "WHERE playerId = :playerId AND name <> :name"
    )
    suspend fun matchIdsNaming(playerId: String, name: String): List<String>

    @Update
    suspend fun update(match: MatchEntity)

    @Update
    suspend fun updateStat(stat: PlayerMatchStatsEntity)
}
