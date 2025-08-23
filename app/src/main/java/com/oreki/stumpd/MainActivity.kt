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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
            color = Color.Gray,
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
                containerColor = Color(0xFF4CAF50)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Start Match",
                    tint = MaterialTheme.colorScheme.surface
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
                containerColor = MaterialTheme.colorScheme.tertiary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Match History",
                    tint = MaterialTheme.colorScheme.surface
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.tertiary
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
                containerColor = if (matchCount > 0) Color(0xFF9C27B0) else Color(0xFFBDBDBD)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Statistics",
                    tint = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Statistics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

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
                containerColor = Color(0xFF9E9E9E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About",
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
        ) {
            Icon(Icons.Default.Add, contentDescription = "Manage Players")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Players", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // App version info
        Text(
            text = "Version 1.0.0",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = "by LogPoseLabs",
            fontSize = 12.sp,
            color = Color.Gray
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
