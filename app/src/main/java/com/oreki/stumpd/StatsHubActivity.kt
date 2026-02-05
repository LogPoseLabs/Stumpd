package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme

class StatsHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatsHubScreen(onBack = { finish() })
                }
            }
        }
    }
}

data class StatsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val categories = listOf(
        StatsCategory(
            title = "Player Rankings",
            description = "Compare players by batting, bowling & fielding stats",
            icon = Icons.Default.Leaderboard,
            route = "player_rankings"
        ),
        StatsCategory(
            title = "Head to Head",
            description = "Batsman vs Bowler matchup statistics",
            icon = Icons.Default.Compare,
            route = "head_to_head"
        ),
        StatsCategory(
            title = "Captain Stats",
            description = "Win/loss records as captain",
            icon = Icons.Default.Star,
            route = "captain_stats"
        ),
        StatsCategory(
            title = "Records",
            description = "Highest scores, best bowling & more",
            icon = Icons.Default.EmojiEvents,
            route = "records"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Statistics", fontWeight = FontWeight.Bold)
                        Text(
                            "Analyze player & match data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(categories) { category ->
                StatsCategoryCard(
                    category = category,
                    onClick = {
                        when (category.route) {
                            "player_rankings" -> {
                                context.startActivity(Intent(context, StatsActivity::class.java))
                            }
                            "head_to_head" -> {
                                context.startActivity(Intent(context, HeadToHeadActivity::class.java))
                            }
                            "captain_stats" -> {
                                context.startActivity(Intent(context, CaptainStatsActivity::class.java))
                            }
                            "records" -> {
                                context.startActivity(Intent(context, RecordsActivity::class.java))
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StatsCategoryCard(
    category: StatsCategory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
