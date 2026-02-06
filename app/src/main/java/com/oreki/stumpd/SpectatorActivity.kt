package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.sync.realtime.RealTimeMatchListener
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.google.gson.Gson
import kotlinx.coroutines.launch

/**
 * Spectator Activity - Read-only live view of a match
 * Updates in real-time as the match progresses
 */
class SpectatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val matchId = intent.getStringExtra("MATCH_ID") ?: ""
        val ownerId = intent.getStringExtra("OWNER_ID") ?: ""
        val shareCode = intent.getStringExtra("SHARE_CODE") ?: ""
        
        if (matchId.isEmpty() || ownerId.isEmpty()) {
            Log.e("SpectatorActivity", "Missing matchId or ownerId")
            finish()
            return
        }

        setContent {
            StumpdTheme {
                SpectatorScreen(
                    matchId = matchId,
                    ownerId = ownerId,
                    shareCode = shareCode,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectatorScreen(
    matchId: String,
    ownerId: String,
    shareCode: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    var matchState by remember { mutableStateOf<com.oreki.stumpd.data.local.entity.InProgressMatchEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<Long>(0L) }
    
    // Start real-time listener for in-progress match
    LaunchedEffect(matchId) {
        try {
            val listener = RealTimeMatchListener()
            
            // Listen to IN-PROGRESS match
            listener.listenToInProgressMatch(ownerId, matchId).collect { match ->
                if (match != null) {
                    val newTimestamp = System.currentTimeMillis()
                    Log.d("SpectatorActivity", "=== Match update received ===")
                    Log.d("SpectatorActivity", "Match ID: ${match.matchId}")
                    Log.d("SpectatorActivity", "Innings: ${match.currentInnings}")
                    Log.d("SpectatorActivity", "Over: ${match.currentOver}.${match.ballsInOver}")
                    Log.d("SpectatorActivity", "Wickets: ${match.totalWickets}")
                    Log.d("SpectatorActivity", "Last updated diff: ${newTimestamp - lastUpdated}ms")
                    
                    matchState = match
                    lastUpdated = newTimestamp
                    isLoading = false
                } else {
                    Log.w("SpectatorActivity", "Received null match update")
                }
            }
        } catch (e: Exception) {
            Log.e("SpectatorActivity", "Failed to start listener", e)
            errorMessage = "Failed to connect: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Live Match Spectator")
                        if (shareCode.isNotEmpty()) {
                            Text(
                                "Code: $shareCode",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting to live match...")
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️ Connection Error",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                matchState == null -> {
                    Text(
                        text = "Match not found or ended",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    // Show match data - use key to force recomposition on data changes
                    key(lastUpdated) {
                        LiveInProgressMatchView(matchState!!, lastUpdated)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveInProgressMatchView(match: com.oreki.stumpd.data.local.entity.InProgressMatchEntity, lastUpdated: Long) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Live", "Scorecard", "Overs", "Partnership")
    
    // Parse match settings
    val matchSettings = remember(match.matchSettingsJson) {
        try {
            Gson().fromJson(match.matchSettingsJson, MatchSettings::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    // Parse team players - explicitly depend on lastUpdated to force re-parsing
    val currentBattingTeam = remember(lastUpdated, match.currentInnings) {
        try {
            val json = if (match.currentInnings == 1) match.team1PlayersJson else match.team2PlayersJson
            Gson().fromJson(json, Array<Player>::class.java).toList()
        } catch (e: Exception) {
            Log.e("SpectatorActivity", "Failed to parse batting team", e)
            emptyList()
        }
    }
    
    val currentBowlingTeam = remember(lastUpdated, match.currentInnings) {
        try {
            val json = if (match.currentInnings == 1) match.team2PlayersJson else match.team1PlayersJson
            Gson().fromJson(json, Array<Player>::class.java).toList()
        } catch (e: Exception) {
            Log.e("SpectatorActivity", "Failed to parse bowling team", e)
            emptyList()
        }
    }
    
    // Parse first innings players (for scorecard in 2nd innings)
    val firstInningsBattingTeam = remember(lastUpdated) {
        try {
            match.firstInningsBattingPlayersJson?.let {
                Gson().fromJson(it, Array<Player>::class.java).toList()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    val firstInningsBowlingTeam = remember(lastUpdated) {
        try {
            match.firstInningsBowlingPlayersJson?.let {
                Gson().fromJson(it, Array<Player>::class.java).toList()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Parse deliveries for overs
    val deliveries = remember(lastUpdated) {
        try {
            match.allDeliveriesJson?.let {
                Gson().fromJson(it, Array<DeliveryUI>::class.java).toList()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        
        // Content based on selected tab - show the scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live indicator
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                MaterialTheme.colorScheme.error,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "LIVE",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Updated ${getTimeAgo(lastUpdated)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Match info
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${match.team1Name} vs ${match.team2Name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${matchSettings?.totalOvers ?: 10} overs per side • Innings ${match.currentInnings}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Current score
            val currentTeam = if (match.currentInnings == 1) match.team1Name else match.team2Name
            val actualTotalRuns = currentBattingTeam.sumOf { it.runs }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        currentTeam,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$actualTotalRuns/${match.totalWickets}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "(${match.currentOver}.${match.ballsInOver})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            when (selectedTabIndex) {
                0 -> Text("Live tab content", Modifier.padding(16.dp))
                1 -> Text("Scorecard - Coming soon", Modifier.padding(16.dp))
                2 -> Text("Overs - Coming soon", Modifier.padding(16.dp))
                3 -> Text("Partnership - Coming soon", Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun LiveMatchView(match: MatchHistory, lastUpdated: Long) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Live indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            MaterialTheme.colorScheme.error,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "LIVE",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Updated ${getTimeAgo(lastUpdated)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Match info
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${match.team1Name} vs ${match.team2Name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(match.matchDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Team 1 score
        ScoreCard(
            teamName = match.team1Name,
            score = match.firstInningsRuns,
            wickets = match.firstInningsWickets
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Team 2 score
        ScoreCard(
            teamName = match.team2Name,
            score = match.secondInningsRuns,
            wickets = match.secondInningsWickets
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Result
        if (match.winnerTeam.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Result",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${match.winnerTeam} won by ${match.winningMargin}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "🔄 Auto-updating live",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BatsmanRow(
    name: String,
    runs: Int,
    balls: Int,
    isStriker: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isStriker) {
                Text(
                    "*",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                name,
                fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal
            )
        }
        
        Text(
            "$runs ($balls)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ScoreCard(teamName: String, score: Int, wickets: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                teamName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "$score/$wickets",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

fun getTimeAgo(timestamp: Long): String {
    val secondsAgo = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        secondsAgo < 5 -> "just now"
        secondsAgo < 60 -> "${secondsAgo}s ago"
        else -> "${secondsAgo / 60}m ago"
    }
}
