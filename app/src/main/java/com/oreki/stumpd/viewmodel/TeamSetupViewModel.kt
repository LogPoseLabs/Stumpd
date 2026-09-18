package com.oreki.stumpd.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.oreki.stumpd.*
import com.oreki.stumpd.data.preferences.MatchSettingsManager
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class TeamSetupViewModel(
    application: Application,
    private val defaultGroupId: String?,
    /**
     * A tournament fixture to play, when the screen was opened from one.
     *
     * A usable preset changes this screen from "pick two teams" to "confirm the rules and toss":
     * the group, the names, the squads and the captains are all decided already, and letting any
     * of them be edited here would mean a match that didn't match its fixture.
     */
    presetOrNull: TeamSetupPreset? = null,
) : AndroidViewModel(application) {

    /** Null unless a *complete* preset arrived — half of one is worse than none. */
    val preset: TeamSetupPreset? = presetOrNull?.takeIf { it.isUsable }

    /** True when the teams are the fixture's and may not be re-picked. */
    val isFixture: Boolean get() = preset != null

    private val db = StumpdDb.get(application)
    private val groupRepo = GroupRepository(db)
    val matchRepo = MatchRepository(db, application)
    private val playerRepo = PlayerRepository(db, matchRepo)
    private val settingsManager = MatchSettingsManager(application)
    val gson = Gson()

    // ── Toast events ─────────────────────────────────────────────────
    private val _toastEvent = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 8)
    val toastEvent = _toastEvent.asSharedFlow()

    // ── State ────────────────────────────────────────────────────────
    var matchSettings by mutableStateOf(settingsManager.getDefaultMatchSettings())
    var team1 by mutableStateOf(Team("Team A", mutableListOf()))
    var team2 by mutableStateOf(Team("Team B", mutableListOf()))
    var jokerPlayer by mutableStateOf<Player?>(null)

    // Dialog states
    var showTeam1Dialog by mutableStateOf(false)
    var showTeam2Dialog by mutableStateOf(false)
    var showJokerDialog by mutableStateOf(false)

    // Text fields (mirroring matchSettings for editable text)
    var oversText by mutableStateOf(matchSettings.totalOvers.toString())
    var maxOversPerBowlerText by mutableStateOf(matchSettings.maxOversPerBowler.toString())
    var wideRunsText by mutableStateOf(matchSettings.wideRuns.toString())
    var noballRunsText by mutableStateOf(matchSettings.noballRuns.toString())
    var byeRunsText by mutableStateOf(matchSettings.byeRuns.toString())
    var legByeRunsText by mutableStateOf(matchSettings.legByeRuns.toString())
    var powerplayOversText by mutableStateOf(matchSettings.powerplayOvers.toString())
    var jokerMaxOversText by mutableStateOf(matchSettings.jokerMaxOvers.toString())

    var selectedGroup by mutableStateOf<GroupEntity?>(null)

    var tossWinner by mutableStateOf<String?>(null)
    var tossChoice by mutableStateOf<String?>(null)

    var groups by mutableStateOf<List<GroupEntity>>(emptyList())
    var allPlayers by mutableStateOf<Map<String, String>>(emptyMap()) // id->name

    var expandedSections by mutableStateOf(setOf("basic"))

    var allowedIds by mutableStateOf<Set<String>>(emptySet())

    // ── Init ─────────────────────────────────────────────────────────
    init {
        loadGroups()
    }

    private fun loadGroups() {
        viewModelScope.launch {
            groups = groupRepo.listGroups()
            val presetGroupId = preset?.groupId
            if (presetGroupId != null) {
                selectedGroup = groups.firstOrNull { it.id == presetGroupId }
            } else if (defaultGroupId != null && selectedGroup == null) {
                selectedGroup = groups.firstOrNull { it.id == defaultGroupId }
            }
            selectedGroup?.let { loadPlayersForGroup(it) }
            selectedGroup?.let { loadDefaultSettings(it) }
            updateAllowedIds()
            applyPreset()
        }
    }

    /**
     * Fills both sides from the fixture.
     *
     * Names come from the whole player table rather than from [allPlayers], because that map is
     * built from the group's *available* players — and availability is a match-day flag, while a
     * tournament squad was fixed when the tournament was planned. A squad member marked away for
     * the day still has to appear in their team.
     */
    private suspend fun applyPreset() {
        val fixture = preset ?: return
        val names = playerRepo.getAllPlayers().associate { it.id to it.name }

        fun squad(ids: List<String>): MutableList<Player> = ids.mapNotNull { id ->
            names[id]?.let { Player(id = PlayerId(id), name = it) }
        }.toMutableList()

        team1 = Team(name = fixture.homeTeamName, players = squad(fixture.homeSquad))
        team2 = Team(name = fixture.awayTeamName, players = squad(fixture.awaySquad))
        // A tournament squad is fixed, so there is nobody spare to be the joker.
        jokerPlayer = null

        val missing = (fixture.homeSquad.size - team1.players.size) +
            (fixture.awaySquad.size - team2.players.size)
        if (missing > 0) {
            _toastEvent.emit(
                ToastEvent.Long("$missing player(s) in this fixture's squads no longer exist.")
            )
        }
    }

    /**
     * The captain of a side, by the name it is playing under.
     *
     * A fixture's captain is an id chosen when the squad was picked, so it never goes through
     * [extractCaptainFromTeamName] — which only works for the `"<name>'s Team"` convention and
     * returns null for a real team name like "Warriors".
     */
    fun captainNameFor(teamName: String): String? {
        val fixture = preset ?: return extractCaptainFromTeamName(teamName)
        val captainId = when {
            teamName.equals(fixture.homeTeamName, ignoreCase = true) -> fixture.homeCaptainPlayerId
            teamName.equals(fixture.awayTeamName, ignoreCase = true) -> fixture.awayCaptainPlayerId
            else -> null
        } ?: return null
        val side = if (teamName.equals(fixture.homeTeamName, true)) team1 else team2
        return side.players.firstOrNull { it.id.value == captainId }?.name
    }

    private suspend fun loadPlayersForGroup(group: GroupEntity) {
        allPlayers = groupRepo.getAvailablePlayersForGroup(group.id)
            .associate { it.id to it.name }
    }

    private suspend fun loadDefaultSettings(group: GroupEntity) {
        val gd = groupRepo.getDefaults(group.id)
        val restored = gd?.matchSettingsJson?.let { json ->
            gson.fromJson(json, MatchSettings::class.java)
        } ?: matchSettings

        matchSettings = restored.copy(shortPitch = gd?.shortPitch ?: restored.shortPitch)
        oversText = matchSettings.totalOvers.toString()
        maxOversPerBowlerText = matchSettings.maxOversPerBowler.toString()
        wideRunsText = matchSettings.wideRuns.toString()
        noballRunsText = matchSettings.noballRuns.toString()
        byeRunsText = matchSettings.byeRuns.toString()
        legByeRunsText = matchSettings.legByeRuns.toString()
        powerplayOversText = matchSettings.powerplayOvers.toString()
        jokerMaxOversText = matchSettings.jokerMaxOvers.toString()
    }

    private suspend fun updateAllowedIds() {
        if (selectedGroup != null) {
            allowedIds = groupRepo.getAvailablePlayersForGroup(selectedGroup!!.id)
                .map { it.id }.toSet()
        } else {
            allowedIds = emptySet()
        }
    }

    // ── Actions ──────────────────────────────────────────────────────
    fun toggleSection(section: String) {
        expandedSections = if (expandedSections.contains(section)) {
            expandedSections - section
        } else {
            expandedSections + section
        }
    }

    fun extractCaptainFromTeamName(teamName: String): String? {
        return when {
            teamName.endsWith("'s Team", ignoreCase = true) -> {
                teamName.substringBefore("'s Team").trim()
            }
            teamName.startsWith("Team ", ignoreCase = true) -> {
                teamName.substringAfter("Team ").trim().takeIf { it.isNotEmpty() }
            }
            else -> null
        }
    }

    fun onGroupChanged(group: GroupEntity?) {
        selectedGroup = group
        viewModelScope.launch {
            if (group != null) {
                loadPlayersForGroup(group)
                loadDefaultSettings(group)
            } else {
                allPlayers = playerRepo.getAllPlayers().associate { it.id to it.name }
            }
            updateAllowedIds()
        }
    }

    fun generateRandomTeams() {
        val group = selectedGroup ?: return
        viewModelScope.launch {
            try {
                val availablePlayerIds = groupRepo.getAvailablePlayersForGroup(group.id)
                    .map { it.id }.toSet()

                val result = generateRandomTeamsWithJokerAndCaptains(
                    availablePlayerIds = availablePlayerIds,
                    allPlayers = allPlayers,
                    groupId = group.id,
                    matchRepo = matchRepo
                )

                when (result) {
                    is TeamGenerationResult.Success -> {
                        team1 = team1.copy(
                            name = "${result.team1Captain.name}'s Team",
                            players = result.team1Players.toMutableList()
                        )
                        team2 = team2.copy(
                            name = "${result.team2Captain.name}'s Team",
                            players = result.team2Players.toMutableList()
                        )
                        jokerPlayer = result.jokerPlayer

                        val message = buildString {
                            append("✅ Teams generated!\n")
                            append("👑 ${result.team1Captain.name} vs ${result.team2Captain.name}\n")
                            append("📊 ${result.team1Players.size} vs ${result.team2Players.size}")
                            if (result.jokerPlayer != null) {
                                append("\n🃏 ${result.jokerPlayer.name} is Joker")
                            }
                        }
                        _toastEvent.emit(ToastEvent.Long(message))
                    }
                    TeamGenerationResult.InsufficientPlayers -> {
                        _toastEvent.emit(ToastEvent.Short("❌ Need at least 2 players"))
                    }
                }
            } catch (e: Exception) {
                _toastEvent.emit(ToastEvent.Short("❌ Failed: ${e.message}"))
            }
        }
    }

    fun loadLastTeams() {
        val group = selectedGroup ?: return
        viewModelScope.launch {
            try {
                val lastTeams = groupRepo.loadLastTeams(group.id)
                if (lastTeams != null) {
                    val (t1Ids, t2Ids, teamNames) = lastTeams
                    val t1Players = t1Ids.mapNotNull { pid ->
                        allPlayers[pid]?.let { name ->
                            Player(id = PlayerId(pid), name = name)
                        }
                    }
                    val t2Players = t2Ids.mapNotNull { pid ->
                        allPlayers[pid]?.let { name ->
                            Player(id = PlayerId(pid), name = name)
                        }
                    }

                    team1 = team1.copy(
                        name = teamNames.first,
                        players = t1Players.toMutableList()
                    )
                    team2 = team2.copy(
                        name = teamNames.second,
                        players = t2Players.toMutableList()
                    )
                    _toastEvent.emit(ToastEvent.Short("✅ Loaded last teams"))
                } else {
                    _toastEvent.emit(ToastEvent.Short("⚠️ No previous teams found"))
                }
            } catch (e: Exception) {
                _toastEvent.emit(ToastEvent.Short("❌ Failed to load teams: ${e.message}"))
            }
        }
    }

    fun removePlayerFromTeam1(player: Player) {
        val newPlayers = team1.players.toMutableList()
        newPlayers.remove(player)
        team1 = team1.copy(players = newPlayers)
    }

    fun removePlayerFromTeam2(player: Player) {
        val newPlayers = team2.players.toMutableList()
        newPlayers.remove(player)
        team2 = team2.copy(players = newPlayers)
    }

    fun addPlayersToTeam1(ids: List<String>) {
        val newPlayers = team1.players.toMutableList()
        ids.forEach { pid ->
            allPlayers[pid]?.let { name ->
                newPlayers.add(Player(id = PlayerId(pid), name = name))
            }
        }
        newPlayers.sortBy { it.name.lowercase() }
        team1 = team1.copy(players = newPlayers)
        showTeam1Dialog = false
    }

    fun addPlayersToTeam2(ids: List<String>) {
        val newPlayers = team2.players.toMutableList()
        ids.forEach { pid ->
            allPlayers[pid]?.let { name ->
                newPlayers.add(Player(id = PlayerId(pid), name = name))
            }
        }
        newPlayers.sortBy { it.name.lowercase() }
        team2 = team2.copy(players = newPlayers)
        showTeam2Dialog = false
    }

    fun selectJoker(playerId: String, playerName: String) {
        jokerPlayer = Player(id = PlayerId(playerId), name = playerName, isJoker = true)
        showJokerDialog = false
    }

    fun saveLastTeams() {
        val group = selectedGroup ?: return
        // A fixture's teams are not the group's quick-match teams, and caching them would make
        // "load last teams" offer a tournament line-up for a casual game. Guarded here rather
        // than at the call site so no future caller can forget.
        if (isFixture) return
        viewModelScope.launch {
            groupRepo.saveLastTeams(
                group.id,
                team1.players.map { it.id.value },
                team2.players.map { it.id.value },
                team1.name,
                team2.name
            )
        }
    }

    /**
     * Validates the match setup and returns an error message if invalid,
     * or null if valid.
     */
    fun validateMatchSetup(): String? {
        val minPlayersPerTeam = 2
        val team1Size = team1.players.size
        val team2Size = team2.players.size

        if (selectedGroup == null) {
            return "Please select a group (e.g., Saturday or Sunday)"
        }
        if (team1Size < minPlayersPerTeam || team2Size < minPlayersPerTeam) {
            return "Each team needs at least $minPlayersPerTeam player!"
        }
        if (team1Size != team2Size) {
            return "Both team needs to have same number of players"
        }
        // Refuse settings the match cannot actually be bowled under, rather than letting the
        // scorer discover at over 4 that nobody is left who can legally bowl.
        matchSettings.bowlingCapacityProblem(
            team1Name = team1.name,
            team1Size = team1Size,
            team2Name = team2.name,
            team2Size = team2Size,
        )?.let { return it }
        return null
    }

    /**
     * Build the final match settings with calculated maxPlayersPerTeam.
     */
    fun buildFinalMatchSettings(): MatchSettings {
        // The squad that actually turned out, not floored at eleven: this is what the wicket
        // margin is measured against, and flooring it claimed more wickets than a side had
        // batters ("won by 9 wickets" in an eight-a-side game).
        return matchSettings.copy(
            maxPlayersPerTeam = maxOf(team1.players.size, team2.players.size)
        )
    }

    // ── Business logic: team generation ──────────────────────────────
    private suspend fun generateRandomTeamsWithJokerAndCaptains(
        availablePlayerIds: Set<String>,
        allPlayers: Map<String, String>,
        groupId: String,
        matchRepo: MatchRepository
    ): TeamGenerationResult {

        val availablePlayers = availablePlayerIds.mapNotNull { playerId ->
            allPlayers[playerId]?.let { name ->
                Player(id = PlayerId(playerId), name = name)
            }
        }

        if (availablePlayers.size < 2) {
            return TeamGenerationResult.InsufficientPlayers
        }

        val startOfDay = startOfLocalDayMillis(System.currentTimeMillis())
        val todaysMatches = try {
            matchRepo.getAllMatches(groupId, limit = 20)
                .filter { it.matchDate >= startOfDay }
        } catch (e: Exception) {
            emptyList()
        }

        val todaysJokers = todaysMatches
            .mapNotNull { it.jokerPlayerName }
            .filter { it.isNotEmpty() }
            .toSet()

        val todaysCaptains = todaysMatches
            .flatMap { match ->
                listOfNotNull(
                    match.team1CaptainName ?: extractCaptainFromTeamName(match.team1Name),
                    match.team2CaptainName ?: extractCaptainFromTeamName(match.team2Name)
                )
            }
            .toSet()

        val isOddNumberOfPlayers = availablePlayers.size % 2 == 1

        val (playersForTeams, joker) = if (isOddNumberOfPlayers) {
            val jokerCandidates = availablePlayers.filter { player ->
                !todaysJokers.contains(player.name)
            }
            val pickedJoker = if (jokerCandidates.isNotEmpty()) {
                jokerCandidates.random()
            } else {
                availablePlayers.random()
            }
            val remainingPlayers = availablePlayers.filter { it.id.value != pickedJoker.id.value }
            Pair(remainingPlayers, pickedJoker.copy(isJoker = true))
        } else {
            Pair(availablePlayers, null)
        }

        val teamSize = playersForTeams.size / 2
        val shuffledPlayers = playersForTeams.shuffled()
        val team1Players = shuffledPlayers.take(teamSize)
        val team2Players = shuffledPlayers.drop(teamSize)

        val team1CaptainCandidates = team1Players.filter { !todaysCaptains.contains(it.name) }
        val team2CaptainCandidates = team2Players.filter { !todaysCaptains.contains(it.name) }

        val team1Captain = if (team1CaptainCandidates.isNotEmpty()) {
            team1CaptainCandidates.random()
        } else {
            team1Players.random()
        }

        val team2Captain = if (team2CaptainCandidates.isNotEmpty()) {
            team2CaptainCandidates.random()
        } else {
            team2Players.random()
        }

        return TeamGenerationResult.Success(
            team1Players = team1Players,
            team2Players = team2Players,
            jokerPlayer = joker,
            team1Captain = team1Captain,
            team2Captain = team2Captain
        )
    }
}

/**
 * Midnight of [nowMillis] in [zone].
 *
 * Joker and captain rotation only looks at matches played "today", so this boundary decides
 * who is still eligible. It has to be the player's local midnight: the previous
 * `now - (now % 86_400_000)` shortcut returned midnight *UTC*, which lands at 05:30 in IST
 * (dropping early-morning matches) and on the previous afternoon in US timezones (counting
 * yesterday's matches as today, exhausting the rotation pool early).
 */
internal fun startOfLocalDayMillis(
    nowMillis: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Long = Calendar.getInstance(zone).apply {
    timeInMillis = nowMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
