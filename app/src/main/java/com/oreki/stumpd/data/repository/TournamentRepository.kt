package com.oreki.stumpd.data.repository

import android.util.Log
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.SyncProgressEntity
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.local.entity.TournamentFixtureEntity
import com.oreki.stumpd.data.local.entity.TournamentSquadPlayerEntity
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity
import com.oreki.stumpd.data.mappers.oversToBalls
import com.oreki.stumpd.data.util.GsonProvider
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.wasAllOut
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.tournament.FixtureStage
import com.oreki.stumpd.domain.tournament.FixtureStatus
import com.oreki.stumpd.domain.tournament.MatchOutcome
import com.oreki.stumpd.domain.tournament.PlannedFixture
import com.oreki.stumpd.domain.tournament.PointsRule
import com.oreki.stumpd.domain.tournament.StandingsRow
import com.oreki.stumpd.domain.tournament.TeamDraft
import com.oreki.stumpd.domain.tournament.TeamRef
import com.oreki.stumpd.domain.tournament.TournamentFixture
import com.oreki.stumpd.domain.tournament.TournamentFormat
import com.oreki.stumpd.domain.tournament.TournamentMatchResult
import com.oreki.stumpd.domain.tournament.TournamentProblem
import com.oreki.stumpd.domain.tournament.autoAdvanceByes
import com.oreki.stumpd.domain.tournament.computeStandings
import com.oreki.stumpd.domain.tournament.decodeFixtureSource
import com.oreki.stumpd.domain.tournament.encodeFixtureSource
import com.oreki.stumpd.domain.tournament.planFixtures
import com.oreki.stumpd.domain.tournament.resolveFixtureSlots
import com.oreki.stumpd.domain.tournament.resolveTournamentOutcome
import com.oreki.stumpd.domain.tournament.tournamentIsComplete
import com.oreki.stumpd.domain.tournament.validateSetup
import com.oreki.stumpd.domain.tournament.validateReadyToPlay
import com.oreki.stumpd.domain.tournament.validateTeams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Tournaments: storing them, and deriving the table from the matches they produced.
 *
 * The derivation is the important half. Standings and the knockout bracket are recomputed from
 * `matches` on every read, exactly as every other aggregate in this app is — so a correction that
 * changes who won a semi-final changes the final too, with no hook to remember and nothing to
 * invalidate. `tournament_fixtures.winnerTeamId` is only a cache, refreshed on read so a synced
 * copy shows non-owners the same bracket.
 */
class TournamentRepository(
    private val db: StumpdDb,
    private val matchRepository: MatchRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private companion object {
        const val TAG = "TournamentRepo"
    }

    /** A tournament as the screens want it: the row, its teams with squads, and its fixtures. */
    data class Bundle(
        val tournament: TournamentEntity,
        val teams: List<TournamentTeamEntity>,
        val squads: Map<String, List<String>>,
        val fixtures: List<TournamentFixture>,
    ) {
        val format: TournamentFormat
            get() = runCatching { TournamentFormat.valueOf(tournament.format) }
                .getOrDefault(TournamentFormat.SINGLE_ROUND_ROBIN)

        val teamRefs: List<TeamRef>
            get() = teams.map {
                TeamRef(
                    teamId = it.teamId,
                    seed = it.seed,
                    name = it.name,
                    poolOrdinal = it.poolOrdinal,
                )
            }
    }

    // ── Reading ─────────────────────────────────────────────────────────────────────────

    suspend fun tournamentsForGroup(groupId: String?): List<TournamentEntity> =
        withContext(ioDispatcher) {
            if (groupId == null) db.tournamentDao().allTournaments()
            else db.tournamentDao().tournamentsForGroup(groupId)
        }

    /**
     * Loads a tournament with its bracket brought up to date.
     *
     * The slots that could be filled in are filled in and byes advanced — on read, so the same
     * answer comes out whether or not anything happened to prompt it. The owner's copy is written
     * back so the version that syncs matches what they see; a non-owner just gets the derived view.
     */
    suspend fun bundle(tournamentId: String, persistDerived: Boolean = true): Bundle? =
        withContext(ioDispatcher) {
            val dao = db.tournamentDao()
            val tournament = dao.tournament(tournamentId) ?: return@withContext null
            val teams = dao.teams(tournamentId)
            val squads = dao.squads(tournamentId)
                .groupBy { it.teamId }
                .mapValues { (_, rows) -> rows.sortedBy { it.battingOrder }.map { it.playerId } }
            val stored = dao.fixtures(tournamentId).map { it.toDomain() }

            val results = resultsFor(stored, teams)
            val derived = deriveFixtures(stored, teams, results, tournament)

            if (persistDerived && derived != stored) {
                runCatching {
                    dao.upsertFixtures(derived.map { it.toEntity(tournamentId) })
                }.onFailure { Log.w(TAG, "Couldn't persist the derived bracket", it) }
            }

            Bundle(
                tournament = tournament,
                teams = teams,
                squads = squads,
                fixtures = derived,
            )
        }

    /** The table, per pool. Pool 0 is "the whole tournament" for formats without pools. */
    suspend fun standings(tournamentId: String): Map<Int, List<StandingsRow>> =
        withContext(ioDispatcher) {
            val bundle = bundle(tournamentId, persistDerived = false)
                ?: return@withContext emptyMap()
            standingsOf(bundle)
        }

    private suspend fun standingsOf(bundle: Bundle): Map<Int, List<StandingsRow>> {
        val results = resultsFor(bundle.fixtures, bundle.teams)
        val points = PointsRule(
            win = bundle.tournament.pointsWin,
            tie = bundle.tournament.pointsTie,
            loss = bundle.tournament.pointsLoss,
            noResult = bundle.tournament.pointsNoResult,
        )
        val ballsPerInnings = 6 * oversOf(bundle.tournament)

        return bundle.teamRefs.groupBy { it.poolOrdinal }.mapValues { (_, poolTeams) ->
            val ids = poolTeams.map { it.teamId }.toSet()
            computeStandings(
                teams = poolTeams,
                // Only matches between two teams of this pool count towards its table.
                results = results.filter { it.teamAId in ids && it.teamBId in ids },
                points = points,
                allottedBallsPerInnings = ballsPerInnings,
            )
        }
    }

    /**
     * The matches this tournament has actually produced, for a stats page.
     *
     * Read from `matches.tournamentId` rather than walking fixtures — every match a fixture
     * settles is stamped with it, and it's one query instead of a join through fixture ids.
     * Deliberately not cached in [Bundle]: a stats tab is opened far less often than the fixtures
     * or the table, so there is no reason to pay for it on every load.
     */
    suspend fun matchesFor(tournamentId: String): List<MatchHistory> = withContext(ioDispatcher) {
        matchRepository.getAllMatchesWithStats().filter { it.tournamentId == tournamentId }
    }

    // ── Writing ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a tournament in draft, with placeholder teams for the scorer to name and fill.
     *
     * The match rules are snapshotted from the group's defaults now, so every fixture is played
     * under the same ones — a mid-season change to the group can't make the table's net run rates
     * incomparable.
     */
    suspend fun create(
        groupId: String,
        name: String,
        format: TournamentFormat,
        teamCount: Int,
        squadSize: Int,
        poolCount: Int = 0,
        advancePerPool: Int = 0,
        matchSettingsJson: String? = null,
    ): Result<String> = withContext(ioDispatcher) {
        validateSetup(format, teamCount, squadSize, poolCount, advancePerPool)?.let {
            return@withContext Result.failure(TournamentException(it))
        }
        if (name.isBlank()) {
            return@withContext Result.failure(
                TournamentException(TournamentProblem("NO_NAME", "Give the tournament a name."))
            )
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val tournament = TournamentEntity(
            tournamentId = id,
            groupId = groupId,
            name = name.trim(),
            format = format.name,
            teamCount = teamCount,
            squadSize = squadSize,
            poolCount = if (format == TournamentFormat.GROUPS_KNOCKOUT) poolCount else 0,
            advancePerPool = if (format == TournamentFormat.GROUPS_KNOCKOUT) advancePerPool else 0,
            status = "DRAFT",
            matchSettingsJson = matchSettingsJson,
            createdAt = now,
            updatedAt = now,
        )
        val teams = (1..teamCount).map { seed ->
            TournamentTeamEntity(
                teamId = teamId(id, seed),
                tournamentId = id,
                name = "Team $seed",
                seed = seed,
                updatedAt = now,
            )
        }
        db.tournamentDao().replaceTournament(tournament, teams, emptyList(), emptyList())
        Result.success(id)
    }

    /**
     * Names the teams and fills their squads.
     *
     * Refused once any fixture has been played: a team's name is part of the primary key of its
     * players' stats rows, so renaming a side after a match would orphan that match's scorecard —
     * which renders as empty innings, with no error at all.
     */
    suspend fun saveTeams(tournamentId: String, drafts: List<TeamDraft>): Result<Unit> =
        withContext(ioDispatcher) {
            val dao = db.tournamentDao()
            val tournament = dao.tournament(tournamentId)
                ?: return@withContext Result.failure(
                    TournamentException(TournamentProblem("NO_TOURNAMENT", "That tournament is gone."))
                )
            validateTeams(drafts, tournament.squadSize)?.let {
                return@withContext Result.failure(TournamentException(it))
            }

            val existing = dao.teams(tournamentId)
            if (dao.playedFixtureCount(tournamentId) > 0) {
                val renamed = existing.filterIndexed { index, team ->
                    drafts.getOrNull(index)?.name?.trim()?.equals(team.name, true) == false
                }
                if (renamed.isNotEmpty()) {
                    return@withContext Result.failure(
                        TournamentException(
                            TournamentProblem(
                                "NAME_LOCKED",
                                "${renamed.first().name} has already played, and results are " +
                                    "recorded against the name it played under. It can't be renamed now.",
                            )
                        )
                    )
                }
            }

            val now = System.currentTimeMillis()
            val teams = drafts.mapIndexed { index, draft ->
                val seed = index + 1
                TournamentTeamEntity(
                    teamId = teamId(tournamentId, seed),
                    tournamentId = tournamentId,
                    name = draft.name.trim(),
                    captainPlayerId = draft.captainPlayerId,
                    captainName = draft.captainName(),
                    seed = seed,
                    poolOrdinal = existing.firstOrNull { it.seed == seed }?.poolOrdinal ?: 0,
                    updatedAt = now,
                )
            }
            val squads = teams.flatMapIndexed { index, team ->
                drafts[index].playerIds.mapIndexed { order, playerId ->
                    TournamentSquadPlayerEntity(
                        tournamentId = tournamentId,
                        teamId = team.teamId,
                        playerId = playerId,
                        battingOrder = order + 1,
                    )
                }
            }
            dao.deleteSquads(tournamentId)
            dao.upsertTeams(teams)
            dao.upsertSquadPlayers(squads)
            dao.touch(tournamentId, now)
            Result.success(Unit)
        }

    /**
     * Generates the schedule.
     *
     * Refused once a fixture has been played, because fixture ids are deterministic — regenerating
     * would hand an existing id to a different pairing, and the match already filed against it
     * would silently belong to the wrong fixture.
     */
    suspend fun generateFixtures(tournamentId: String): Result<Int> = withContext(ioDispatcher) {
        val dao = db.tournamentDao()
        val tournament = dao.tournament(tournamentId)
            ?: return@withContext Result.failure(
                TournamentException(TournamentProblem("NO_TOURNAMENT", "That tournament is gone."))
            )
        if (dao.playedFixtureCount(tournamentId) > 0) {
            return@withContext Result.failure(
                TournamentException(
                    TournamentProblem(
                        "FIXTURES_LOCKED",
                        "A fixture has already been played, so the schedule can't be regenerated.",
                    )
                )
            )
        }

        val format = runCatching { TournamentFormat.valueOf(tournament.format) }
            .getOrDefault(TournamentFormat.SINGLE_ROUND_ROBIN)
        val teams = dao.teams(tournamentId)
        val squads = dao.squads(tournamentId).groupBy { it.teamId }
        val drafts = teams.map { team ->
            TeamDraft(
                name = team.name,
                playerIds = squads[team.teamId].orEmpty().map { it.playerId },
                captainPlayerId = team.captainPlayerId,
            )
        }
        // Full squads and settled captains, or a schedule means nothing — this is the
        // completeness [validateTeams] deliberately skips on every incremental save.
        validateReadyToPlay(drafts, tournament.squadSize)?.let {
            return@withContext Result.failure(TournamentException(it))
        }

        val refs = teams.map {
            TeamRef(teamId = it.teamId, seed = it.seed, name = it.name, poolOrdinal = it.poolOrdinal)
        }
        val planned = planFixtures(
            format = format,
            teams = refs,
            poolCount = tournament.poolCount,
            advancePerPool = tournament.advancePerPool,
        )
        if (planned.isEmpty()) {
            return@withContext Result.failure(
                TournamentException(
                    TournamentProblem("NO_FIXTURES", "That combination produces no fixtures.")
                )
            )
        }

        val now = System.currentTimeMillis()
        // Pool allocation is part of the plan, so write it back onto the teams.
        val pooled = planned
            .filter { it.stage == FixtureStage.POOL }
            .flatMap { listOfNotNull(it.homeTeamId to it.poolOrdinal, it.awayTeamId?.to(it.poolOrdinal)) }
            .toMap()
        if (pooled.isNotEmpty()) {
            dao.upsertTeams(
                teams.map { team ->
                    team.copy(poolOrdinal = pooled[team.teamId] ?: team.poolOrdinal, updatedAt = now)
                }
            )
        }

        dao.deleteFixtures(tournamentId)
        dao.upsertFixtures(planned.map { it.toEntity(tournamentId, now) })
        dao.setStatus(tournamentId, "ACTIVE", now)
        Result.success(planned.count { !it.isBye })
    }

    /**
     * Files a played match against its fixture.
     *
     * Best-effort by design: the caller runs this *after* the match is saved, and a tournament
     * bookkeeping failure must never cost somebody their scorecard. Idempotent, so a correction or
     * a re-save recomputes rather than duplicating.
     */
    suspend fun recordFixtureResult(fixtureId: String, matchId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val dao = db.tournamentDao()
                val fixture = dao.fixture(fixtureId) ?: error("No fixture $fixtureId")
                val now = System.currentTimeMillis()
                dao.upsertFixtures(
                    listOf(
                        fixture.copy(
                            matchId = matchId,
                            status = FixtureStatus.COMPLETED.name,
                            updatedAt = now,
                        )
                    )
                )
                // The bracket and the table are derived on read; this refreshes the stored copy so
                // the version that syncs agrees with what the owner sees.
                bundle(fixture.tournamentId)?.let { refreshed ->
                    val status = if (tournamentIsComplete(refreshed.fixtures)) "COMPLETE" else "ACTIVE"
                    dao.setStatus(fixture.tournamentId, status, now)
                }
                dao.touch(fixture.tournamentId, now)
            }
        }

    suspend fun delete(tournamentId: String) = withContext(ioDispatcher) {
        db.tournamentDao().deleteTournamentGraph(tournamentId)
    }

    /**
     * Drops the upload watermark for a tournament.
     *
     * Called after a deletion so the row doesn't linger claiming a tournament is already
     * uploaded — which would matter if one were ever recreated with the same id.
     */
    suspend fun forgetUploadProgress(tournamentId: String) = withContext(ioDispatcher) {
        db.syncProgressDao().clear(SyncProgressEntity.COLLECTION_TOURNAMENTS, tournamentId)
    }

    // ── Deriving ────────────────────────────────────────────────────────────────────────

    /**
     * The played fixtures, reduced to what a table needs.
     *
     * Team identity comes from the match's stored ids when it has them, and falls back to matching
     * the team names — for a match linked by hand, or saved before the ids existed.
     */
    private suspend fun resultsFor(
        fixtures: List<TournamentFixture>,
        teams: List<TournamentTeamEntity>,
    ): List<TournamentMatchResult> {
        val played = fixtures.mapNotNull { it.matchId }
        if (played.isEmpty()) return emptyList()

        val matches = matchRepository.getAllMatchesWithStats()
            .filter { it.id in played }
            .associateBy { it.id }
        if (matches.isEmpty()) return emptyList()

        val ballsByMatchAndTeam = db.tournamentDao()
            .bowlingSpells(matches.keys.toList())
            .groupBy { it.matchId to it.team }
            .mapValues { (_, spells) -> spells.sumOf { it.oversBowled.oversToBalls() } }

        fun teamIdFor(match: MatchHistory, name: String, storedId: String?): String? =
            storedId ?: teams.firstOrNull { it.name.equals(name, ignoreCase = true) }?.teamId

        return fixtures.mapNotNull { fixture ->
            val match = matches[fixture.matchId] ?: return@mapNotNull null
            val aId = teamIdFor(match, match.team1Name, match.team1Id) ?: return@mapNotNull null
            val bId = teamIdFor(match, match.team2Name, match.team2Id) ?: return@mapNotNull null
            val (outcome, winnerId) = resolveTournamentOutcome(
                winnerTeam = match.winnerTeam,
                teamAId = aId,
                teamAName = match.team1Name,
                teamBId = bId,
                teamBName = match.team2Name,
            )
            // Balls faced by a side are the balls its opponent bowled.
            val aBalls = ballsByMatchAndTeam[match.id to match.team2Name] ?: 0
            val bBalls = ballsByMatchAndTeam[match.id to match.team1Name] ?: 0
            TournamentMatchResult(
                matchId = match.id,
                teamAId = aId,
                teamBId = bId,
                outcome = outcome,
                winnerTeamId = winnerId,
                teamARuns = match.firstInningsRuns,
                teamABalls = aBalls,
                teamAAllOut = wasAllOut(match, innings = 1),
                teamBRuns = match.secondInningsRuns,
                teamBBalls = bBalls,
                teamBAllOut = wasAllOut(match, innings = 2),
            )
        }
    }

    /** Fills in whatever the results now determine, and advances byes. Idempotent. */
    private suspend fun deriveFixtures(
        stored: List<TournamentFixture>,
        teams: List<TournamentTeamEntity>,
        results: List<TournamentMatchResult>,
        tournament: TournamentEntity,
    ): List<TournamentFixture> {
        val byMatch = results.associateBy { it.matchId }
        val withWinners = stored.map { fixture ->
            val result = fixture.matchId?.let { byMatch[it] }
            when {
                result == null -> fixture
                // A no-result leaves the slot undecided rather than guessing.
                result.outcome == MatchOutcome.WIN ->
                    fixture.copy(winnerTeamId = result.winnerTeamId, status = FixtureStatus.COMPLETED)

                else -> fixture.copy(winnerTeamId = null, status = FixtureStatus.COMPLETED)
            }
        }

        val advanced = autoAdvanceByes(withWinners)
        val refs = teams.map {
            TeamRef(teamId = it.teamId, seed = it.seed, name = it.name, poolOrdinal = it.poolOrdinal)
        }
        val points = PointsRule(
            win = tournament.pointsWin,
            tie = tournament.pointsTie,
            loss = tournament.pointsLoss,
            noResult = tournament.pointsNoResult,
        )
        val standingsByPool = refs.groupBy { it.poolOrdinal }.mapValues { (_, poolTeams) ->
            val ids = poolTeams.map { t -> t.teamId }.toSet()
            computeStandings(
                teams = poolTeams,
                results = results.filter { it.teamAId in ids && it.teamBId in ids },
                points = points,
                allottedBallsPerInnings = 6 * oversOf(tournament),
            )
        }
        // A pool only seeds a knockout once every one of its fixtures is settled.
        val completedPools = advanced
            .filter { it.stage == FixtureStage.POOL }
            .groupBy { it.poolOrdinal }
            .filterValues { pool ->
                pool.all { it.status == FixtureStatus.COMPLETED || it.status == FixtureStatus.BYE }
            }
            .keys

        return resolveFixtureSlots(advanced, standingsByPool, completedPools)
    }

    // ── Mapping ─────────────────────────────────────────────────────────────────────────

    private fun teamId(tournamentId: String, seed: Int) = "$tournamentId:t$seed"

    /** The captain's name, when the draft carried the squad's names alongside its ids. */
    private fun TeamDraft.captainName(): String? {
        val index = playerIds.indexOf(captainPlayerId)
        return if (index >= 0) playerNames.getOrNull(index) else null
    }

    /**
     * The overs a fixture is played over, from the rules snapshotted at creation.
     *
     * Net run rate charges a side bowled out the full quota, so the table needs to know what the
     * quota was — and five is the app's own default.
     */
    private fun oversOf(tournament: TournamentEntity): Int =
        tournament.matchSettingsJson
            ?.let { json ->
                runCatching {
                    GsonProvider.get().fromJson(json, MatchSettings::class.java).totalOvers
                }.getOrNull()
            }
            ?.takeIf { it > 0 }
            ?: 5

    private fun TournamentFixtureEntity.toDomain() = TournamentFixture(
        fixtureId = fixtureId,
        stage = runCatching { FixtureStage.valueOf(stage) }.getOrDefault(FixtureStage.LEAGUE),
        poolOrdinal = poolOrdinal,
        round = round,
        slot = slot,
        leg = leg,
        homeTeamId = homeTeamId,
        awayTeamId = awayTeamId,
        homeSource = decodeFixtureSource(homeSourceRef),
        awaySource = decodeFixtureSource(awaySourceRef),
        label = label,
        status = runCatching { FixtureStatus.valueOf(status) }.getOrDefault(FixtureStatus.PENDING),
        matchId = matchId,
        winnerTeamId = winnerTeamId,
    )

    private fun TournamentFixture.toEntity(
        tournamentId: String,
        updatedAt: Long = System.currentTimeMillis(),
    ) = TournamentFixtureEntity(
        fixtureId = fixtureId,
        tournamentId = tournamentId,
        stage = stage.name,
        poolOrdinal = poolOrdinal,
        round = round,
        slot = slot,
        leg = leg,
        homeTeamId = homeTeamId,
        awayTeamId = awayTeamId,
        homeSourceRef = homeSource?.let { encodeFixtureSource(it) },
        awaySourceRef = awaySource?.let { encodeFixtureSource(it) },
        label = label,
        status = status.name,
        matchId = matchId,
        winnerTeamId = winnerTeamId,
        updatedAt = updatedAt,
    )

    private fun PlannedFixture.toEntity(
        tournamentId: String,
        updatedAt: Long = System.currentTimeMillis(),
    ) = TournamentFixtureEntity(
        // Deterministic, so re-uploading a tournament overwrites rather than duplicating.
        fixtureId = "$tournamentId:${stage.name}:$round:$slot:$leg",
        tournamentId = tournamentId,
        stage = stage.name,
        poolOrdinal = poolOrdinal,
        round = round,
        slot = slot,
        leg = leg,
        homeTeamId = homeTeamId,
        awayTeamId = awayTeamId,
        homeSourceRef = homeSource?.let { encodeFixtureSource(it) },
        awaySourceRef = awaySource?.let { encodeFixtureSource(it) },
        label = label,
        status = status.name,
        updatedAt = updatedAt,
    )
}

/** A refusal with a sentence fit to show the user. */
class TournamentException(val problem: TournamentProblem) : Exception(problem.message)
