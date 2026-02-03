package com.oreki.stumpd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.oreki.stumpd.ui.theme.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.manager.InProgressMatchManager
import com.oreki.stumpd.data.models.MatchInProgress
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.theme.ResultChip
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import com.oreki.stumpd.ui.theme.sectionContainer
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MatchHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MatchHistoryScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchHistoryScreen() {
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val repo = rememberMatchRepository()
    val groupRepo = rememberGroupRepository()
    val inProgressManager = remember { InProgressMatchManager(context) }

    var allMatches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }
    var inProgressMatch by remember { mutableStateOf<MatchInProgress?>(null) }

    // Group filter state
    data class GroupFilter(val id: String?, val name: String) // id == null => All Groups
    var selectedFilter by remember { mutableStateOf(GroupFilter(id = null, name = "All Groups")) }
    var showGroupPicker by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<PlayerGroup>>(emptyList()) }

    LaunchedEffect(Unit) {
        val summaries = groupRepo.listGroupSummaries()
        groups = summaries.map { (g, d, _) -> g.toDomain(d, emptyList()) }
        
        // Load default group from Room DB
        val defaultGroupId = groupRepo.getDefaultGroupId()
        if (defaultGroupId != null) {
            val groupName = groups.firstOrNull { it.id == defaultGroupId }?.name ?: "All Groups"
            selectedFilter = GroupFilter(id = defaultGroupId, name = groupName)
        }
        
        // Load ALL matches (we'll filter in UI based on selectedFilter)
        allMatches = repo.getAllMatches(groupId = null)
        inProgressMatch = inProgressManager.loadMatch()
    }

    // Apply filter
    val filteredMatches = remember(allMatches, selectedFilter) {
        when {
            selectedFilter.id == null -> allMatches
            else -> allMatches.filter { it.groupId == selectedFilter.id }
        }
    }

    // Calculate quick stats
    val totalMatches = filteredMatches.size
    val completedMatches = filteredMatches.count { it.winnerTeam.isNotBlank() }
    val inProgressCount = if (inProgressMatch != null) 1 else 0

    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Match History",
                subtitle = "$totalMatches matches${if (selectedFilter.id != null) " in ${selectedFilter.name}" else ""}",
                onBack = { (context as ComponentActivity).finish() },
                actions = {
                    IconButton(onClick = { showGroupPicker = true }) {
                        Badge(
                            containerColor = if (selectedFilter.id != null)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Transparent
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Filter by Group",
                                tint = if (selectedFilter.id != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Quick Stats Card (show even if no matches for better UX)
            if (totalMatches > 0 || inProgressMatch != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = totalMatches.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Matches",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        
                        if (inProgressCount > 0) {
                            VerticalDivider(
                                modifier = Modifier.height(56.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = inProgressCount.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "In Progress",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Group Filter Chip
            if (groups.isNotEmpty()) {
                FilterChip(
                    selected = selectedFilter.id != null,
                    onClick = { showGroupPicker = true },
                    label = { Text(selectedFilter.name, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (filteredMatches.isEmpty() && inProgressMatch == null) {
                // Modern empty state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Matches Yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (selectedFilter.id) {
                                null -> "Start scoring to build your match history!"
                                else -> "No matches in ${selectedFilter.name}."
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(context, MainActivity::class.java)
                                context.startActivity(intent)
                                (context as ComponentActivity).finish()
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start a Match")
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Show in-progress match at the top
                    inProgressMatch?.let { match ->
                        item {
                            InProgressMatchCard(
                                match = match,
                                onResume = {
                                    val intent = android.content.Intent(context, ScoringActivity::class.java)
                                    intent.putExtra("resume_match_id", match.matchId)
                                    intent.putExtra("match_id", match.matchId)
                                    intent.putExtra("team1_name", match.team1Name)
                                    intent.putExtra("team2_name", match.team2Name)
                                    intent.putExtra("joker_name", match.jokerName)
                                    intent.putExtra("team1_players", match.team1PlayerNames.toTypedArray())
                                    intent.putExtra("team2_players", match.team2PlayerNames.toTypedArray())
                                    intent.putExtra("team1_player_ids", match.team1PlayerIds.toTypedArray())
                                    intent.putExtra("team2_player_ids", match.team2PlayerIds.toTypedArray())
                                    intent.putExtra("match_settings", match.matchSettingsJson)
                                    intent.putExtra("group_id", match.groupId ?: "")
                                    intent.putExtra("group_name", match.groupName ?: "")
                                    intent.putExtra("toss_winner", match.tossWinner ?: "")
                                    intent.putExtra("toss_choice", match.tossChoice ?: "")
                                    context.startActivity(intent)
                                },
                                onDiscard = {
                                    scope.launch {
                                        inProgressManager.clearMatch()
                                        inProgressMatch = null
                                        Toast.makeText(context, "Unfinished match discarded", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                    
                    // Show completed matches
                    items(filteredMatches) { match ->
                        MatchHistoryCard(
                            match = match,
                            onDelete = {
                                scope.launch {
                                    repo.deleteMatch(match.id)
                                    // Reload all matches to keep filtering consistent
                                    allMatches = repo.getAllMatches(groupId = null)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Group picker dialog
    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("Filter by Group") },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
                    // All Groups
                    item {
                        ListItem(
                            headlineContent = { Text("All Groups") },
                            modifier = Modifier.clickable {
                                selectedFilter = GroupFilter(id = null, name = "All Groups")
                                showGroupPicker = false
                            }
                        )
                    }
                    // Real groups
                    items(groups) { g ->
                        val count = allMatches.count { it.groupId == g.id }
                        ListItem(
                            headlineContent = { Text(g.name) },
                            supportingContent = {
                                Text("$count matches", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.clickable {
                                selectedFilter = GroupFilter(id = g.id, name = g.name)
                                showGroupPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGroupPicker = false }) { Text("Close") } }
        )
    }
}

@Composable
fun MatchHistoryCard(
    match: MatchHistory,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val context = LocalContext.current
    val isTie = match.winnerTeam.equals("TIE", true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header with date, time and group
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            dateFormat.format(Date(match.matchDate)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        timeFormat.format(Date(match.matchDate)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Group badge
                    match.groupName?.takeIf { it.isNotBlank() }?.let { gName ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                gName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Match",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Match Result Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (isTie)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isTie) Icons.Default.Info else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isTie)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isTie) {
                            "Match Tied • ${match.winningMargin}"
                        } else {
                            "${match.winnerTeam} won by ${match.winningMargin}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTie)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Teams and scores - Modern design
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.team1Name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${match.firstInningsRuns}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/${match.firstInningsWickets}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                
                // VS Divider
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "VS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                
                // Team 2
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = match.team2Name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${match.secondInningsRuns}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/${match.secondInningsWickets}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            // Player of the Match (if available)
            match.playerOfTheMatchName?.let { potm ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Player of the Match",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                potm,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        match.playerOfTheMatchImpact?.let { impact ->
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    "%.1f".format(impact),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Joker badge (if present)
            match.jokerPlayerName?.let { jokerName ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🃏", fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Joker: $jokerName",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons - Direct navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // View Full Scorecard - Primary action
                FilledTonalButton(
                    onClick = {
                        val intent = android.content.Intent(context, FullScorecardActivity::class.java)
                        intent.putExtra("match_id", match.id)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Full Scorecard",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Quick Summary - Secondary action
                OutlinedButton(
                    onClick = {
                        val intent = android.content.Intent(context, MatchDetailActivity::class.java)
                        intent.putExtra("match_id", match.id)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Summary",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun InProgressMatchCard(
    match: MatchInProgress,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with "IN PROGRESS" badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", fontSize = 14.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "IN PROGRESS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                    
                    match.groupName?.takeIf { it.isNotBlank() }?.let { gName ->
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Text(
                                gName,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = onDiscard,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Discard Match",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            // Teams and score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.team1Name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "vs",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = match.team2Name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${match.calculatedTotalRuns}/${match.totalWickets}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Overs: ${match.currentOver}.${match.ballsInOver}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Innings ${match.currentInnings}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Resume button
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Home,  // Using Home as play icon placeholder
                    contentDescription = "Resume",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Resume Match", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last saved: ${dateFormat.format(Date(match.lastSavedAt))}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
