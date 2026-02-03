package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.BuildConfig
import com.oreki.stumpd.data.update.AppUpdateManager
import com.oreki.stumpd.data.update.UpdateInfo
import com.oreki.stumpd.data.update.UpdateState
import kotlinx.coroutines.launch

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AboutScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Update manager
    val updateManager = remember { AppUpdateManager(context) }
    val updateState by updateManager.updateState.collectAsState()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "About Stump'd",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            context.startActivity(intent)
                            (context as ComponentActivity).finish()
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                // App Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🏏",
                            fontSize = 72.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Stump'd",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Your Digital Cricket Scorebook",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Check for Updates Button
                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdate = true
                                    updateCheckMessage = null
                                    val result = updateManager.checkForUpdate()
                                    isCheckingUpdate = false
                                    if (result != null) {
                                        updateInfo = result
                                        showUpdateDialog = true
                                    } else {
                                        updateCheckMessage = "You're on the latest version!"
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking...")
                            } else {
                                Icon(
                                    Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Check for Updates")
                            }
                        }
                        
                        // Show message if already up to date
                        updateCheckMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            item {
                // Description Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "What is Stump'd?",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Stump'd is a comprehensive cricket scoring application designed for players, coaches, and enthusiasts. Track every ball, analyze performance, and maintain detailed match records with our intuitive Material Design interface.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Perfect for casual matches, league games, or tournament play. Whether you're scoring at the ground or reviewing past performances, Stump'd has you covered!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            item {
                // Features Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Key Features",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val featureCategories = listOf(
                            "Match Scoring" to listOf(
                                "Live ball-by-ball scoring",
                                "Real-time statistics & analytics",
                                "Swipe navigation between tabs",
                                "Undo last delivery",
                                "Powerplay tracking & doubling"
                            ),
                            "Player Management" to listOf(
                                "Advanced team & player management",
                                "Player group system",
                                "Unique joker player mechanics",
                                "Availability toggle per group",
                                "Auto-sync statistics"
                            ),
                            "Match Intelligence" to listOf(
                                "Strike rate & economy calculations",
                                "Live & required run rates",
                                "Target calculations",
                                "Chase tracker",
                                "Wicket type categorization"
                            ),
                            "Data & History" to listOf(
                                "Comprehensive match history",
                                "Detailed digital scorecards",
                                "Group-based filtering",
                                "Backup & restore functionality",
                                "Ball-by-ball replay data"
                            ),
                            "Modern UI" to listOf(
                                "Material Design 3",
                                "Adaptive dark/light themes",
                                "Modernized dialogs & cards",
                                "Responsive layouts",
                                "Beautiful animations"
                            )
                        )

                        featureCategories.forEachIndexed { index, (category, features) ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            Text(
                                text = category,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            features.forEach { feature ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "• ",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = feature,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "About LogPoseLabs",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "We're passionate about creating simple, elegant solutions for sports enthusiasts. Stump'd is designed to make cricket scoring accessible and enjoyable for players of all levels.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Built with modern Android development practices using Kotlin, Jetpack Compose, and Material Design 3.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Support & Feedback",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Have suggestions or found a bug? We'd love to hear from you!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "📧 logposelabs@gmail.com",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                // Enhanced footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Built with ❤️ for cricket lovers",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "© 2025 LogPoseLabs. All rights reserved.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Update Dialog - placed outside Scaffold
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { 
                if (!updateInfo!!.isForceUpdate && updateState !is UpdateState.Downloading) {
                    showUpdateDialog = false
                }
            },
            icon = {
                when (updateState) {
                    is UpdateState.Downloading -> {
                        CircularProgressIndicator(
                            progress = { (updateState as UpdateState.Downloading).progress / 100f },
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                    }
                    is UpdateState.Error -> {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            title = {
                Text(
                    when (updateState) {
                        is UpdateState.Downloading -> "Downloading..."
                        is UpdateState.ReadyToInstall -> "Ready to Install"
                        is UpdateState.Installing -> "Installing..."
                        is UpdateState.Error -> "Update Failed"
                        else -> "Update Available"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (updateState) {
                        is UpdateState.Downloading -> {
                            Text("Downloading version ${updateInfo!!.latestVersionName}...")
                            LinearProgressIndicator(
                                progress = { (updateState as UpdateState.Downloading).progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${(updateState as UpdateState.Downloading).progress.toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is UpdateState.Error -> {
                            Text(
                                (updateState as UpdateState.Error).message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text("Current: v${BuildConfig.VERSION_NAME}")
                            Text("Latest: v${updateInfo!!.latestVersionName}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                updateInfo!!.updateMessage,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (updateState) {
                    is UpdateState.Downloading -> {
                        TextButton(onClick = { updateManager.cancelDownload() }) {
                            Text("Cancel")
                        }
                    }
                    is UpdateState.Error -> {
                        Button(onClick = {
                            scope.launch {
                                updateManager.downloadAndInstall(updateInfo!!)
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                    is UpdateState.ReadyToInstall, is UpdateState.Installing -> {
                        // No button needed
                    }
                    else -> {
                        Button(onClick = {
                            scope.launch {
                                updateManager.downloadAndInstall(updateInfo!!)
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update Now")
                        }
                    }
                }
            },
            dismissButton = {
                if (!updateInfo!!.isForceUpdate && updateState !is UpdateState.Downloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Later")
                    }
                }
            }
        )
    }
}
