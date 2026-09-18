package com.oreki.stumpd.ui.tournament

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity
import com.oreki.stumpd.domain.model.TeamSetupPreset
import com.oreki.stumpd.domain.tournament.FixtureSource
import com.oreki.stumpd.domain.tournament.FixtureStage
import com.oreki.stumpd.domain.tournament.FixtureStatus
import com.oreki.stumpd.domain.tournament.StandingsRow
import com.oreki.stumpd.domain.tournament.TournamentFixture
import com.oreki.stumpd.domain.tournament.poolName
import com.oreki.stumpd.ui.theme.EmptyState
import com.oreki.stumpd.ui.theme.MicroLabel
import com.oreki.stumpd.ui.theme.PrimaryCta
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.hairline
import com.oreki.stumpd.viewmodel.TournamentViewModel
import kotlinx.coroutines.launch

/**
 * One tournament: its fixtures, its table and its teams.
 *
 * Three views of the same object, so they are tabs rather than three routes.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TournamentDetailScreen(
    tournamentId: String,
    onBack: () -> Unit,
    onEditSquad: (String) -> Unit,
    onPlayFixture: (TeamSetupPreset) -> Unit,
    onOpenMatch: (String) -> Unit,
) {
    val vm: TournamentViewModel = tournamentViewModel(tournamentId)
    val snackbarHostState = remember { SnackbarHostState() }
    val tabs = listOf("Fixtures", "Table", "Teams", "Stats")
    val pagerState = rememberPagerState(initialPage = vm.selectedTab, pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // Written back on every swipe or tap, so a screen pushed on top of this one — the squad
    // editor — and popped back off returns to the tab that was actually showing.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { vm.selectedTab = it }
    }

    LifecycleResumeEffect(Unit) {
        // A fixture played elsewhere in the app changes both the schedule and the table.
        vm.reload()
        onPauseOrDispose { }
    }

    vm.problem?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            vm.problem = null
        }
    }
    vm.notice?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            vm.notice = null
        }
    }

    val bundle = vm.bundle

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StumpdTopBar(
                title = bundle?.tournament?.name ?: "Tournament",
                subtitle = bundle?.tournament?.let {
                    "${it.format.formatLabel()} · ${it.teamCount} teams · ${it.squadSize} a side"
                },
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.loading && bundle == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                bundle == null -> EmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = "Tournament not found",
                    description = "It may have been deleted on another device.",
                )

                else -> {
                    if (!vm.canEdit) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                "Read-only — this group's owner runs its tournaments.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                        title,
                                        fontWeight = if (pagerState.currentPage == index) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Medium
                                        },
                                    )
                                },
                            )
                        }
                    }
                    // A pager rather than a plain `when`, so the tabs swipe the way every other
                    // tabbed screen in the app does — Fixtures/Table/Teams/Stats otherwise being
                    // tap-only would be the one screen that didn't.
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            0 -> FixturesTab(vm, onPlayFixture, onOpenMatch)
                            1 -> StandingsTab(vm)
                            2 -> TeamsTab(vm, onEditSquad)
                            else -> TournamentStatsTab(vm)
                        }
                    }
                }
            }
        }
    }
}

// ── Fixtures ────────────────────────────────────────────────────────────────────────────

@Composable
private fun FixturesTab(
    vm: TournamentViewModel,
    onPlay: (TeamSetupPreset) -> Unit,
    onOpenMatch: (String) -> Unit,
) {
    val bundle = vm.bundle ?: return
    val teamNames = bundle.teams.associate { it.teamId to it.name }
    val squadsComplete = bundle.teams.all { team ->
        vm.squadOf(team.teamId).size == bundle.tournament.squadSize &&
            team.captainPlayerId != null
    }

    if (bundle.fixtures.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            EmptyState(
                icon = Icons.Default.PlayArrow,
                title = "No fixtures yet",
                description = if (squadsComplete) {
                    "Generate the schedule once you're happy with the teams."
                } else {
                    "Name every team and fill its squad first — the schedule is seeded from them."
                },
            )
            if (vm.canEdit) {
                PrimaryCta(
                    text = "Generate fixtures",
                    enabled = squadsComplete,
                    onClick = { vm.generateFixtures() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bundle.fixtures.groupBy { it.stage to it.round }.forEach { (key, fixtures) ->
            item {
                val (stage, round) = key
                val heading = when {
                    fixtures.any { it.poolOrdinal > 0 } ->
                        "Group ${poolName(fixtures.first().poolOrdinal)} · Round $round"

                    stage == FixtureStage.KNOCKOUT -> fixtures.first().label
                    else -> "Round $round"
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    heading.uppercase(),
                    style = MicroLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fixtures.forEach { fixture ->
                item {
                    val playable = vm.canEdit && fixture.status == FixtureStatus.PENDING
                    FixtureRow(
                        fixture = fixture,
                        teamNames = teamNames,
                        onPlay = if (playable) {
                            { vm.presetFor(fixture.fixtureId)?.let(onPlay) }
                        } else {
                            null
                        },
                        onOpenMatch = fixture.matchId?.let { matchId -> { onOpenMatch(matchId) } },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FixtureRow(
    fixture: TournamentFixture,
    teamNames: Map<String, String>,
    onPlay: (() -> Unit)?,
    onOpenMatch: (() -> Unit)?,
) {
    /** A slot's occupant, or what it is waiting for. */
    fun sideLabel(teamId: String?, source: FixtureSource?): String = when {
        teamId != null -> teamNames[teamId] ?: "Unknown team"
        source is FixtureSource.WinnerOf -> "Winner of match ${source.slot + 1}"
        source is FixtureSource.PoolPosition ->
            "${ordinal(source.position)} in Group ${poolName(source.poolOrdinal)}"

        else -> "To be decided"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpenMatch != null) Modifier.clickable { onOpenMatch() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = hairline(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fixture.label,
                    style = MicroLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                if (fixture.status == FixtureStatus.BYE) {
                    Text(
                        "${sideLabel(fixture.homeTeamId, fixture.homeSource)} — bye",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "${sideLabel(fixture.homeTeamId, fixture.homeSource)}  v  " +
                            sideLabel(fixture.awayTeamId, fixture.awaySource),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                fixture.winnerTeamId?.let { winner ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${teamNames[winner] ?: "Someone"} won",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (onPlay != null) {
                FilledTonalButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Play")
                }
            } else {
                StatusChip(fixture.status)
            }
        }
    }
}

@Composable
private fun StatusChip(status: FixtureStatus) {
    val (label, colour) = when (status) {
        FixtureStatus.COMPLETED -> "Played" to MaterialTheme.colorScheme.primary
        FixtureStatus.BYE -> "Bye" to MaterialTheme.colorScheme.onSurfaceVariant
        FixtureStatus.IN_PROGRESS -> "Live" to MaterialTheme.colorScheme.tertiary
        FixtureStatus.AWAITING_TEAMS -> "Waiting" to MaterialTheme.colorScheme.onSurfaceVariant
        FixtureStatus.PENDING -> "To play" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = colour)
}

// ── The table ───────────────────────────────────────────────────────────────────────────

@Composable
private fun StandingsTab(vm: TournamentViewModel) {
    val bundle = vm.bundle ?: return
    if (vm.standings.isEmpty()) {
        EmptyState(
            icon = Icons.Default.EmojiEvents,
            title = "No table yet",
            description = "It fills in as fixtures are played.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.standings.entries.sortedBy { it.key }.forEach { (pool, rows) ->
            item {
                Spacer(Modifier.height(4.dp))
                if (pool > 0) {
                    Text(
                        "GROUP ${poolName(pool)}",
                        style = MicroLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StandingsTable(rows)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * The table.
 *
 * Built by hand, like every other table in this app — there is no table composable. Ties and
 * no-results only get a column when one has actually happened, which keeps the common case narrow
 * enough to read on a phone.
 */
@Composable
private fun StandingsTable(rows: List<StandingsRow>) {
    val showTies = rows.any { it.tied > 0 }
    val showNoResults = rows.any { it.noResult > 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = hairline(),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Cell("", 0.5f)
                Cell("Team", 2.4f, header = true)
                Cell("P", 0.6f, header = true)
                Cell("W", 0.6f, header = true)
                Cell("L", 0.6f, header = true)
                if (showTies) Cell("T", 0.6f, header = true)
                if (showNoResults) Cell("NR", 0.7f, header = true)
                Cell("Pts", 0.8f, header = true)
                Cell("NRR", 1.1f, header = true)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cell("${index + 1}", 0.5f)
                    Cell(row.teamName, 2.4f, bold = true)
                    Cell("${row.played}", 0.6f)
                    Cell("${row.won}", 0.6f)
                    Cell("${row.lost}", 0.6f)
                    if (showTies) Cell("${row.tied}", 0.6f)
                    if (showNoResults) Cell("${row.noResult}", 0.7f)
                    Cell("${row.points}", 0.8f, bold = true)
                    Cell(
                        row.netRunRate?.let { String.format("%+.2f", it) } ?: "—",
                        1.1f,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    header: Boolean = false,
    bold: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = if (header) MaterialTheme.typography.labelSmall
        else MaterialTheme.typography.bodySmall,
        fontWeight = if (bold || header) FontWeight.SemiBold else FontWeight.Normal,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        textAlign = if (weight > 2f) TextAlign.Start else TextAlign.Center,
    )
}

// ── Teams ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun TeamsTab(vm: TournamentViewModel, onEditSquad: (String) -> Unit) {
    val bundle = vm.bundle ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                if (vm.hasPlayedFixtures) {
                    "Names are settled — results are recorded against them. Squads can still change."
                } else {
                    "Name each team and pick its squad. The schedule is seeded from this order."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        bundle.teams.forEach { team ->
            item {
                TeamRow(
                    team = team,
                    squadSize = bundle.tournament.squadSize,
                    filled = vm.squadOf(team.teamId).size,
                    captain = vm.nameOf(team.captainPlayerId),
                    enabled = vm.canEdit,
                    onClick = { onEditSquad(team.teamId) },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TeamRow(
    team: TournamentTeamEntity,
    squadSize: Int,
    filled: Int,
    captain: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = hairline(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    team.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (captain.isBlank()) "No captain yet" else "Captain: $captain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "$filled/$squadSize",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (filled == squadSize) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun ordinal(position: Int): String = when (position) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${position}th"
}

// ── Stats ───────────────────────────────────────────────────────────────────────────────

/**
 * Leaders within this tournament only.
 *
 * Built from the same per-player figures the rest of the app uses ([PlayerRepository]'s
 * career aggregator), just fed this tournament's own matches instead of a group's or a career's —
 * so a player's tournament runs are the same number the scorecards already agree on, not a second
 * calculation that could drift from them.
 */
@Composable
private fun TournamentStatsTab(vm: TournamentViewModel) {
    LaunchedEffect(Unit) { vm.loadStatsIfNeeded() }

    if (!vm.statsLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val batters = vm.playerStats.filter { it.totalRuns > 0 }.sortedByDescending { it.totalRuns }
    val bowlers = vm.playerStats.filter { it.totalWickets > 0 }.sortedByDescending { it.totalWickets }
    val fielders = vm.playerStats.filter { it.totalCatches > 0 }.sortedByDescending { it.totalCatches }

    if (batters.isEmpty() && bowlers.isEmpty() && fielders.isEmpty() && vm.potmTally.isEmpty()) {
        EmptyState(
            icon = Icons.Default.EmojiEvents,
            title = "No stats yet",
            description = "They fill in as fixtures are played.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (batters.isNotEmpty()) {
            item {
                LeaderboardSection(title = "Most Runs") {
                    batters.take(10).forEachIndexed { index, player ->
                        LeaderboardRow(
                            rank = index + 1,
                            name = player.name,
                            statLine = "${player.totalMatches} inn · " +
                                "avg ${formatStat(player.battingAverage)} · " +
                                "SR ${formatStat(player.strikeRate)}",
                            value = "${player.totalRuns}",
                        )
                    }
                }
            }
        }
        if (bowlers.isNotEmpty()) {
            item {
                LeaderboardSection(title = "Most Wickets") {
                    bowlers.take(10).forEachIndexed { index, player ->
                        LeaderboardRow(
                            rank = index + 1,
                            name = player.name,
                            statLine = "avg ${formatStat(player.bowlingAverage)} · " +
                                "econ ${formatStat(player.economyRate)}",
                            value = "${player.totalWickets}",
                        )
                    }
                }
            }
        }
        if (fielders.isNotEmpty()) {
            item {
                LeaderboardSection(title = "Most Catches") {
                    fielders.take(10).forEachIndexed { index, player ->
                        LeaderboardRow(
                            rank = index + 1,
                            name = player.name,
                            statLine = "${player.totalMatches} matches",
                            value = "${player.totalCatches}",
                        )
                    }
                }
            }
        }
        if (vm.potmTally.isNotEmpty()) {
            item {
                LeaderboardSection(title = "Player of the Match") {
                    vm.potmTally.take(10).forEachIndexed { index, entry ->
                        LeaderboardRow(
                            rank = index + 1,
                            name = entry.name,
                            statLine = null,
                            value = "${entry.count}",
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** "12.5" rather than "12.50000" or "0" for a figure nobody has earned yet. */
private fun formatStat(value: Double): String = if (value <= 0.0) "—" else String.format("%.1f", value)

@Composable
private fun LeaderboardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MicroLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            border = hairline(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, name: String, statLine: String?, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#$rank",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            statLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
