package com.oreki.stumpd

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import com.oreki.stumpd.ui.theme.StumpdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val storageManager = remember { MatchStorageManager(context) }
    val matchCount = remember { storageManager.getAllMatches().size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Title with Cricket Theme
        Text(
            text = "🏏",
            fontSize = 60.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Stump'd",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Your Digital Cricket Scorebook",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Show match count if any matches exist
        if (matchCount > 0) {
            Text(
                text = "$matchCount matches played",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(48.dp))
        }

        // Start New Match Button
        Button(
            onClick = {
                val intent = Intent(context, TeamSetupActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Start Match",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start New Match",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Match History Button
        Button(
            onClick = {
                val intent = Intent(context, MatchHistoryActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Match History",
                    tint = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Match History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                if (matchCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = matchCount.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Statistics Button
        Button(
            onClick = {
                if (matchCount > 0) {
                    val intent = Intent(context, StatsActivity::class.java)
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Play some matches first to see statistics!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (matchCount > 0)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Statistics",
                    tint = MaterialTheme.colorScheme.onTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Statistics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Button(
            onClick = {
                val intent = Intent(context, AddPlayerActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Person, contentDescription = "Manage Players")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Players", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val intent = Intent(context, GroupManagementActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Person, contentDescription = "Manage Groups")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Groups", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // About Button
        Button(
            onClick = {
                val intent = Intent(context, AboutActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // App version info
        Text(
            text = "Version 1.0.0",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "by LogPoseLabs",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    StumpdTheme {
        MainScreen()
    }
}
