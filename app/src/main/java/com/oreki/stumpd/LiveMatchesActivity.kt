package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.sync.sharing.MatchSharingManager
import com.oreki.stumpd.data.sync.firebase.EnhancedFirebaseAuthHelper
import com.oreki.stumpd.data.sync.realtime.RealTimeMatchListener
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import kotlinx.coroutines.launch

/**
 * Live Matches Screen
 * Shows all currently shared/active matches that spectators can join
 */
class LiveMatchesActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LiveMatchesScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMatchesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var liveMatches by remember { mutableStateOf<List<SharedMatchInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<SharedMatchInfo?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    
    // Load live matches on start
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val authHelper = EnhancedFirebaseAuthHelper(context)
                var userId = authHelper.currentUserId
                
                if (userId == null) {
                    // Sign in anonymously if needed
                    val user = authHelper.signInAnonymously()
                    userId = user?.uid
                    
                    if (userId == null) {
                        errorMessage = "Authentication failed. Please check your internet connection."
                        isLoading = false
                        return@launch
                    }
                    
                    // Give Firebase a moment to propagate auth state
                    kotlinx.coroutines.delay(500)
                }
                
                currentUserId = userId
                
                val sharingManager = MatchSharingManager()
                val matches = sharingManager.listActiveSharedMatches()
                
                liveMatches = matches
                isLoading = false
            } catch (e: Exception) {
                errorMessage = e.message
                isLoading = false
                android.util.Log.e("LiveMatchesActivity", "Error loading matches", e)
            }
        }
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { matchToDelete ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Live Match?") },
            text = { 
                Text("This will remove \"${matchToDelete.team1Name} vs ${matchToDelete.team2Name}\" from live matches. Spectators will no longer be able to watch this match.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            try {
                                val sharingManager = MatchSharingManager()
                                sharingManager.deleteInProgressMatch(matchToDelete.matchId)
                                
                                // Remove from list
                                liveMatches = liveMatches.filter { it.matchId != matchToDelete.matchId }
                                
                                Toast.makeText(context, "Match deleted successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isDeleting = false
                                showDeleteDialog = null
                            }
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            StumpdTopBar(
                title = "Live Matches",
                subtitle = "Watch ongoing matches in real-time",
                onBack = { (context as? ComponentActivity)?.finish() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Loading live matches...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                errorMessage != null -> {
                    // Error state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Failed to load matches",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            val sharingManager = MatchSharingManager()
                                            val matches = sharingManager.listActiveSharedMatches()
                                            liveMatches = matches
                                            isLoading = false
                                        } catch (e: Exception) {
                                            errorMessage = e.message
                                            isLoading = false
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                liveMatches.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.SportsBaseball,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                "No Live Matches",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "There are no active matches being shared right now.\nCheck back later or ask someone to share their match!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val sharingManager = MatchSharingManager()
                                            val matches = sharingManager.listActiveSharedMatches()
                                            liveMatches = matches
                                            isLoading = false
                                        } catch (e: Exception) {
                                            errorMessage = e.message
                                            isLoading = false
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Refresh")
                            }
                        }
                    }
                }
                else -> {
                    // List of matches
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Column {
                                        Text(
                                            "${liveMatches.size} Live ${if (liveMatches.size == 1) "Match" else "Matches"}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Tap any match to watch live",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Match list
                        items(liveMatches) { match ->
                            LiveMatchCard(
                                match = match,
                                isOwner = currentUserId == match.ownerId,
                                onClick = {
                                    // Open spectator view
                                    val intent = Intent(context, SpectatorActivity::class.java).apply {
                                        putExtra("MATCH_ID", match.matchId)
                                        putExtra("OWNER_ID", match.ownerId)
                                        putExtra("SHARE_CODE", match.shareCode)
                                    }
                                    context.startActivity(intent)
                                },
                                onDelete = {
                                    showDeleteDialog = match
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveMatchCard(
    match: SharedMatchInfo,
    isOwner: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Teams row with delete button for owner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Teams
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            match.team1Name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (match.team1Score != null) {
                            Text(
                                match.team1Score,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Text(
                        "vs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            match.team2Name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (match.team2Score != null) {
                            Text(
                                match.team2Score,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Delete button for owner
                if (isOwner) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete match",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Match info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live indicator
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = MaterialTheme.colorScheme.onError
                                ) {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                            Text(
                                "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                    
                    // Status
                    Text(
                        match.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Owner badge
                    if (isOwner) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "YOUR MATCH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // Watch button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tap to Watch",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Data class for shared match info displayed in the list
 */
data class SharedMatchInfo(
    val shareCode: String,
    val matchId: String,
    val ownerId: String,
    val team1Name: String,
    val team2Name: String,
    val team1Score: String? = null,
    val team2Score: String? = null,
    val status: String = "In Progress",
    val ownerName: String? = null
)
