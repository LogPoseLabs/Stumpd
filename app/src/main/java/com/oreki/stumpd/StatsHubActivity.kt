package com.oreki.stumpd

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.stats.StatsNavHost
import com.oreki.stumpd.ui.stats.StatsRoute
import com.oreki.stumpd.ui.theme.GradientHeroHeader
import com.oreki.stumpd.ui.theme.StumpdTheme
import androidx.navigation.NavController
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class StatsHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            StumpdTheme {
                val windowSizeClass = calculateWindowSizeClass(this@StatsHubActivity)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatsNavHost(
                        onCloseStatsFlow = { finish() },
                        widthSizeClass = windowSizeClass.widthSizeClass
                    )
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
fun StatsHubScreen(
    navController: NavController,
    onBack: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val context = LocalContext.current
    val categories = listOf(
        StatsCategory(
            title = "Player Stats",
            description = "Every player, sortable by any career figure",
            icon = Icons.Default.Leaderboard,
            route = "all_players"
        ),
        StatsCategory(
            title = "Rankings",
            description = "ICC-inspired batting, bowling & all-rounder ratings",
            icon = Icons.Default.EmojiEvents,
            route = "rankings"
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
            icon = Icons.Default.MilitaryTech,
            route = "records"
        )
    )

    Scaffold(
        topBar = {
            // Title lives in the gradient hero below, so the bar only carries navigation.
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (widthSizeClass == WindowWidthSizeClass.Expanded) 4 else 2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GradientHeroHeader(
                    title = "Statistics",
                    subtitle = "Analyze player & match data",
                    emoji = "📊",
                    shape = MaterialTheme.shapes.extraLarge
                )
            }

            items(categories) { category ->
                StatsCategoryCard(
                    category = category,
                    onClick = {
                        when (category.route) {
                            "all_players" -> {
                                // The old "Statistics" screen showed the top five by runs and by
                                // wickets, both of which this list already covers, so the hub
                                // goes straight to the full list.
                                val intent = Intent(context, AllPlayersStatsActivity::class.java)
                                intent.putExtra("sort_by", "Runs")
                                context.startActivity(intent)
                            }
                            "rankings" -> {
                                navController.navigate(StatsRoute.Rankings.route)
                            }
                            "head_to_head" -> {
                                navController.navigate(StatsRoute.HeadToHead.route)
                            }
                            "captain_stats" -> {
                                navController.navigate(StatsRoute.CaptainStats.route)
                            }
                            "records" -> {
                                navController.navigate(StatsRoute.Records.route)
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large,
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
