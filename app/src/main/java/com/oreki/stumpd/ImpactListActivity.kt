package com.oreki.stumpd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme

class ImpactListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val matchId = intent.getStringExtra("match_id") ?: ""
        setContent {
            StumpdTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ImpactListScreen(matchId = matchId)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpactListScreen(matchId: String) {
    val context = LocalContext.current
    val storage = remember { MatchStorageManager(context) }
    val match = remember(matchId) { storage.getAllMatches().find { it.id == matchId } }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Impact - Full List") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (match == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Match not found", fontSize = 18.sp)
            }
            return@Scaffold
        }
        val impacts = match.playerImpacts.sortedByDescending { it.impact }
        if (impacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No impact data available", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(impacts) { pi ->
                ImpactRow(pi = pi, highlight = (pi.name == match.playerOfTheMatchName))
            }
        }
    }
}

@Composable
private fun ImpactRow(pi: PlayerImpact, highlight: Boolean) {
    Card(colors = CardDefaults.cardColors(
        containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    )) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = (if (pi.isJoker) "🃏 " else "") + "${pi.name} (${pi.team})",
                    fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                    text = "%.1f".format(pi.impact),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (pi.summary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = pi.summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}