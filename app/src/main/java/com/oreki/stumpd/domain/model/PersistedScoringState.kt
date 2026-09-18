package com.oreki.stumpd.domain.model

/**
 * Serializable snapshot of partnership / FOW lists persisted with in-progress saves.
 * Kept separate from [DeliverySnapshot] (per-ball undo frames) for clarity and smaller docs.
 */
data class PartnershipsPersistenceState(
    val currentPartnershipRuns: Int = 0,
    val currentPartnershipBalls: Int = 0,
    val currentPartnershipBatsman1Runs: Int = 0,
    val currentPartnershipBatsman2Runs: Int = 0,
    val currentPartnershipBatsman1Balls: Int = 0,
    val currentPartnershipBatsman2Balls: Int = 0,
    val currentPartnershipBatsman1Name: String? = null,
    val currentPartnershipBatsman2Name: String? = null,
    val partnerships: List<Partnership> = emptyList(),
    val fallOfWickets: List<FallOfWicket> = emptyList(),
    val firstInningsPartnerships: List<Partnership> = emptyList(),
    val firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    /**
     * The second innings' stands and wickets, stashed when it ends.
     *
     * They used to stay in the live lists until the match was saved, which a super over would then
     * append to. Defaulted, so a match already in flight resumes without them.
     */
    val secondInningsPartnerships: List<Partnership> = emptyList(),
    val secondInningsFallOfWickets: List<FallOfWicket> = emptyList(),
)

/**
 * Everything a resumed match needs to know about a super over in progress.
 *
 * Its own blob rather than more columns: the eliminator can repeat, so there is no fixed number of
 * innings to make columns for. Every field is defaulted, so a match saved before super overs
 * existed resumes as an ordinary one.
 */
data class SuperOverPersistenceState(
    /** What the side in progress must beat, or null while it is setting the target. */
    val runsToChase: Int? = null,
    /** The second innings' figures, which a super over would otherwise overwrite. */
    val secondInningsRuns: Int = 0,
    val secondInningsWickets: Int = 0,
    val superOvers: List<SuperOverInnings> = emptyList(),
    val superOverWinner: String? = null,
    val completedBattersSuperOver: Map<Int, List<Player>> = emptyMap(),
    val completedBowlersSuperOver: Map<Int, List<Player>> = emptyMap(),
)
