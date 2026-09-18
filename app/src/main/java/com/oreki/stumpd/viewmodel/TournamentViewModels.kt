package com.oreki.stumpd.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.manager.PlayerDetailedStats
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.data.repository.TournamentException
import com.oreki.stumpd.data.repository.TournamentRepository
import com.oreki.stumpd.data.sync.firebase.FirestoreTournamentDao
import com.oreki.stumpd.domain.model.TeamSetupPreset
import com.oreki.stumpd.domain.tournament.PotmEntry
import com.oreki.stumpd.domain.tournament.StandingsRow
import com.oreki.stumpd.domain.tournament.TeamDraft
import com.oreki.stumpd.domain.tournament.TournamentFormat
import com.oreki.stumpd.domain.tournament.potmTally
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The tournament list, and creating one.
 *
 * A tournament belongs to a group, so the list follows the group picker the rest of the app uses.
 */
@HiltViewModel
class TournamentListViewModel @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    var groups by mutableStateOf<List<GroupEntity>>(emptyList())
        private set
    var selectedGroupId by mutableStateOf<String?>(null)
        private set
    var tournaments by mutableStateOf<List<TournamentEntity>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    /** The last refusal, for the screen to show. Cleared once read. */
    var problem by mutableStateOf<String?>(null)

    val selectedGroup: GroupEntity?
        get() = groups.firstOrNull { it.id == selectedGroupId }

    /** Only a group's owner can run its tournament — a non-owner's edits could never upload. */
    val canEdit: Boolean get() = selectedGroup?.isOwner == true

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            loading = true
            groups = groupRepository.listGroups()
            if (selectedGroupId == null) {
                selectedGroupId = groupRepository.getDefaultGroupId()
                    ?: groups.firstOrNull { it.isOwner }?.id
                    ?: groups.firstOrNull()?.id
            }
            tournaments = tournamentRepository.tournamentsForGroup(selectedGroupId)
            loading = false
        }
    }

    fun selectGroup(groupId: String?) {
        selectedGroupId = groupId
        reload()
    }

    /**
     * Creates a tournament, taking its match rules from the group's defaults.
     *
     * Snapshotted now rather than read per fixture, so a later change to the group can't make the
     * table's net run rates incomparable halfway through.
     */
    fun create(
        name: String,
        format: TournamentFormat,
        teamCount: Int,
        squadSize: Int,
        poolCount: Int,
        advancePerPool: Int,
        onCreated: (String) -> Unit,
    ) {
        val groupId = selectedGroupId ?: return
        viewModelScope.launch {
            val settings = groupRepository.getDefaults(groupId)?.matchSettingsJson
            tournamentRepository.create(
                groupId = groupId,
                name = name,
                format = format,
                teamCount = teamCount,
                squadSize = squadSize,
                poolCount = poolCount,
                advancePerPool = advancePerPool,
                matchSettingsJson = settings,
            ).onSuccess { id ->
                reload()
                onCreated(id)
            }.onFailure { problem = it.userMessage() }
        }
    }

    fun delete(tournamentId: String) {
        val groupId = tournaments.firstOrNull { it.tournamentId == tournamentId }?.groupId
        viewModelScope.launch {
            tournamentRepository.delete(tournamentId)
            // Also from the cloud, or the next download would bring it back — the sync applies
            // whatever documents exist, and it has no record of a local deletion. Best-effort:
            // offline, the local delete still stands and the row may reappear after a sync.
            if (groupId != null) {
                runCatching { FirestoreTournamentDao().deleteTournament(groupId, tournamentId) }
                    .onFailure { problem = "Deleted here, but not in the cloud yet." }
            }
            // The upload watermark goes too, so a tournament recreated with the same id would
            // still be treated as pending.
            runCatching {
                tournamentRepository.forgetUploadProgress(tournamentId)
            }
            reload()
        }
    }
}

/**
 * One tournament: its teams, its fixtures and its table.
 *
 * Everything is re-read after a change rather than patched in place, because the bracket and the
 * table are *derived* — from the matches the fixtures produced — and deriving them again is the
 * only way to be sure the screen agrees with the data.
 */
@HiltViewModel(assistedFactory = TournamentViewModel.Factory::class)
class TournamentViewModel @AssistedInject constructor(
    @Assisted("tournament_id") val tournamentId: String,
    private val tournamentRepository: TournamentRepository,
    private val groupRepository: GroupRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("tournament_id") tournamentId: String): TournamentViewModel
    }

    var bundle by mutableStateOf<TournamentRepository.Bundle?>(null)
        private set
    var standings by mutableStateOf<Map<Int, List<StandingsRow>>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set

    /** Every player's name, for squads and captains. */
    var playerNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /**
     * The group's roster — not its *available* players.
     *
     * Availability is a match-day thing; a tournament is planned ahead, and somebody missing one
     * Sunday shouldn't vanish from their team.
     */
    var rosterIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var problem by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    /**
     * Which of Fixtures/Table/Teams/Stats was showing.
     *
     * Held here rather than trusted to the pager's own `rememberSaveable`, because that state is
     * tied to the screen's composition — and pushing the squad editor on top and popping back
     * out of it was landing back on Fixtures instead of wherever the scorer actually was. This
     * view model survives on the nav entry for as long as the entry does, so reading it back as
     * the pager's initial page is the reliable version of the same idea.
     */
    var selectedTab by mutableStateOf(0)

    /** False for a group this device doesn't own: it could never upload the result. */
    var canEdit by mutableStateOf(false)
        private set

    /** True once a fixture has been played: names and the schedule are settled from then on. */
    val hasPlayedFixtures: Boolean
        get() = bundle?.fixtures?.any { it.matchId != null } == true

    /** Every player's aggregated figures across this tournament's own matches — nothing else. */
    var playerStats by mutableStateOf<List<PlayerDetailedStats>>(emptyList())
        private set
    var potmTally by mutableStateOf<List<PotmEntry>>(emptyList())
        private set

    /**
     * Whether [playerStats] reflects the current fixtures.
     *
     * Computing it walks every ball of every match the tournament has produced, which is not
     * worth paying for on every visit to Fixtures or Table — so it loads on request, the first
     * time the Stats tab is opened, and is invalidated by [reload] so a fixture played since is
     * picked up the next time that tab is opened again.
     */
    var statsLoaded by mutableStateOf(false)
        private set

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            loading = true
            val loaded = tournamentRepository.bundle(tournamentId)
            bundle = loaded
            if (loaded != null) {
                standings = tournamentRepository.standings(tournamentId)
                canEdit = groupRepository.getGroupById(loaded.tournament.groupId)?.isOwner == true
                rosterIds = groupRepository.getMembers(loaded.tournament.groupId).map { it.id }.toSet()
            }
            playerNames = playerRepository.getAllPlayers().associate { it.id to it.name }
            statsLoaded = false
            loading = false
        }
    }

    fun loadStatsIfNeeded() {
        if (statsLoaded) return
        viewModelScope.launch {
            val matches = tournamentRepository.matchesFor(tournamentId)
            playerStats = playerRepository.getPlayerDetailedStats(matches)
            potmTally = potmTally(matches)
            statsLoaded = true
        }
    }

    fun saveTeams(drafts: List<TeamDraft>, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            tournamentRepository.saveTeams(tournamentId, drafts)
                .onSuccess {
                    reload()
                    onSaved()
                }
                .onFailure { problem = it.userMessage() }
        }
    }

    fun generateFixtures() {
        viewModelScope.launch {
            tournamentRepository.generateFixtures(tournamentId)
                .onSuccess { count ->
                    notice = "$count ${if (count == 1) "fixture" else "fixtures"} generated"
                    reload()
                }
                .onFailure { problem = it.userMessage() }
        }
    }

    /**
     * The whole field as drafts, with one team's edits applied.
     *
     * Every team goes because the rules that matter are cross-team — a duplicate name, a player in
     * two squads — and the repository maps drafts to seeds **by position**, so the order here is
     * load-bearing: it must be the stored seed order, which is how [Bundle.teams] arrives.
     */
    fun draftsWith(
        teamId: String,
        name: String,
        playerIds: List<String>,
        captainPlayerId: String?,
    ): List<TeamDraft> = bundle?.teams.orEmpty().map { team ->
        if (team.teamId == teamId) {
            draft(name.trim(), playerIds, captainPlayerId)
        } else {
            draft(team.name, squadOf(team.teamId), team.captainPlayerId)
        }
    }

    private fun draft(name: String, playerIds: List<String>, captainPlayerId: String?) =
        TeamDraft(
            name = name,
            playerIds = playerIds,
            // Parallel to the ids, because validation catches two players sharing a name — which
            // scoring would silently collapse into one.
            playerNames = playerIds.map { nameOf(it) },
            captainPlayerId = captainPlayerId,
        )

    /** The squad of a team, as player ids in batting order. */
    fun squadOf(teamId: String): List<String> = bundle?.squads?.get(teamId).orEmpty()

    /** Players already taken by another team in this tournament. */
    fun playersTakenExcept(teamId: String): Set<String> =
        bundle?.squads.orEmpty()
            .filterKeys { it != teamId }
            .values
            .flatten()
            .toSet()

    fun nameOf(playerId: String?): String = playerId?.let { playerNames[it] }.orEmpty()

    /**
     * Everything team setup needs to open on a fixture, or null when it isn't ready.
     *
     * Assembled here rather than in the screen because it must come from stored state: the match
     * that results is attributed to these ids, and a screen guess would attribute it wrongly.
     */
    fun presetFor(fixtureId: String): TeamSetupPreset? {
        val loaded = bundle ?: return null
        val fixture = loaded.fixtures.firstOrNull { it.fixtureId == fixtureId } ?: return null
        val home = loaded.teams.firstOrNull { it.teamId == fixture.homeTeamId } ?: return null
        val away = loaded.teams.firstOrNull { it.teamId == fixture.awayTeamId } ?: return null
        return TeamSetupPreset(
            tournamentId = loaded.tournament.tournamentId,
            tournamentName = loaded.tournament.name,
            fixtureId = fixture.fixtureId,
            fixtureLabel = fixture.label,
            groupId = loaded.tournament.groupId,
            homeTeamId = home.teamId,
            homeTeamName = home.name,
            homeCaptainPlayerId = home.captainPlayerId,
            homePlayerIds = squadOf(home.teamId),
            awayTeamId = away.teamId,
            awayTeamName = away.name,
            awayCaptainPlayerId = away.captainPlayerId,
            awayPlayerIds = squadOf(away.teamId),
        ).takeIf { it.isUsable }
    }
}

/** A refusal's own sentence where there is one, rather than an exception's class name. */
private fun Throwable.userMessage(): String =
    (this as? TournamentException)?.problem?.message ?: message ?: "That didn't work."
