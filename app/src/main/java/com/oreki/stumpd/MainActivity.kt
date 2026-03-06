package com.oreki.stumpd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.manager.ScoringAccessManager
import com.oreki.stumpd.data.update.AppUpdateManager
import com.oreki.stumpd.data.update.UpdateInfo
import com.oreki.stumpd.data.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.oreki.stumpd.data.sync.firebase.FirestoreGroupDao
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oreki.stumpd.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
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

// Data class for menu items
data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val color: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val groupRepo = rememberGroupRepository()
    val matchRepo = rememberMatchRepository()
    val playerRepo = rememberPlayerRepository()
    val scope = rememberCoroutineScope()

    // Load groups and stats
    var groups by remember { mutableStateOf<List<GroupEntity>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<GroupEntity?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var matchCount by remember { mutableStateOf(0) }
    var playerCount by remember { mutableStateOf(0) }

    // Join Group dialog state
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinMessage by remember { mutableStateOf<String?>(null) }

    val scoringAccessManager = remember { ScoringAccessManager(context) }

    // OTA Update state
    val updateManager = remember { AppUpdateManager(context) }
    val updateState by updateManager.updateState.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    // Check for updates on startup
    LaunchedEffect(Unit) {
        val updateInfo = updateManager.checkForUpdate()
        if (updateInfo != null) {
            pendingUpdateInfo = updateInfo
            showUpdateDialog = true
        }
    }

    LaunchedEffect(Unit) {
        groups = groupRepo.listGroups()
        // Get default group from Room DB
        val savedGroupId = groupRepo.getDefaultGroupId()
        selectedGroup = groups.firstOrNull { it.id == savedGroupId }

        // Note: Legacy shared_matches cleanup disabled - using group-based restriction now
        // The old MatchSharingManager used a separate shared_matches collection
        // which is no longer needed with the new memberDeviceIds-based access control
    }

    // Function to refresh stats
    fun refreshStats() {
        scope.launch {
            val currentGroup = selectedGroup
            if (currentGroup != null) {
                // Filter matches by selected group using Room DB
                val groupMatches = matchRepo.getAllMatches(groupId = currentGroup.id)
                matchCount = groupMatches.size

                // Get players in this group
                val groupPlayers = groupRepo.getMembers(currentGroup.id)
                playerCount = groupPlayers.size
            } else {
                // Show all matches and players if no group is selected
                val allMatches = matchRepo.getAllMatches(groupId = null)
                matchCount = allMatches.size

                // Get all players from Room DB
                val allPlayers = playerRepo.getAllPlayers()
                playerCount = allPlayers.size
            }
        }
    }

    // Update stats whenever selectedGroup changes
    LaunchedEffect(selectedGroup) {
        refreshStats()
    }

    // Refresh stats when screen resumes (coming back from other screens)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    // Reload groups in case they changed
                    groups = groupRepo.listGroups()
                    val savedGroupId = groupRepo.getDefaultGroupId()
                    selectedGroup = groups.firstOrNull { it.id == savedGroupId }
                    // Stats will refresh automatically via LaunchedEffect
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update Dialog
    if (showUpdateDialog && pendingUpdateInfo != null) {
        UpdateDialog(
            updateInfo = pendingUpdateInfo!!,
            updateState = updateState,
            onDismiss = {
                if (!pendingUpdateInfo!!.isForceUpdate) {
                    showUpdateDialog = false
                }
            },
            onUpdate = {
                scope.launch {
                    updateManager.downloadAndInstall(pendingUpdateInfo!!)
                }
            },
            onCancel = {
                updateManager.cancelDownload()
            }
        )
    }

    // Force update blocking overlay
    if (pendingUpdateInfo?.isForceUpdate == true && !showUpdateDialog) {
        // Re-show dialog if force update is required
        LaunchedEffect(Unit) {
            showUpdateDialog = true
        }
    }

    // Join Group Dialog (shown directly from Get Started card)
    if (showJoinDialog) {
        var code by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
            title = { Text("Join Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the 6-character invite code shared by the group admin:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            val filtered = it.filter { c -> c.isLetterOrDigit() }
                                .take(6)
                                .uppercase()
                            code = filtered
                        },
                        label = { Text("Invite Code") },
                        placeholder = { Text("ABC123") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 24.sp,
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (joinMessage != null) {
                        Text(
                            joinMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            joinMessage = "Looking for group..."
                            try {
                                val app = context.applicationContext as StumpdApplication
                                val userId = app.syncManager.getUserId()

                                if (userId == null) {
                                    joinMessage = "Please wait for sync to initialize..."
                                    return@launch
                                }

                                if (groupRepo.hasJoinedGroupWithCode(code)) {
                                    joinMessage = "You've already joined this group"
                                    return@launch
                                }

                                val firestoreGroupDao = FirestoreGroupDao()
                                val groupData = withContext(Dispatchers.IO) {
                                    firestoreGroupDao.joinGroupWithInviteCode(code, userId)
                                }

                                if (groupData != null) {
                                    // Save joined group record
                                    groupRepo.joinGroupWithCode(
                                        inviteCode = code,
                                        remoteGroupId = groupData.group.id,
                                        groupName = groupData.group.name
                                    )
                                    // Save full group data so it shows immediately
                                    withContext(Dispatchers.IO) {
                                        val db = app.database
                                        db.groupDao().upsertGroup(groupData.group)
                                        db.groupDao().clearMembers(groupData.group.id)
                                        groupData.members.forEach { member ->
                                            db.groupDao().upsertMembers(listOf(member))
                                        }
                                        groupData.unavailable.forEach { unavailable ->
                                            db.groupDao().markPlayerUnavailable(unavailable)
                                        }
                                        groupData.defaults?.let { defaults ->
                                            db.groupDao().upsertDefaults(defaults)
                                        }
                                    }
                                    // Refresh groups list
                                    groups = groupRepo.listGroups()
                                    if (selectedGroup == null && groups.isNotEmpty()) {
                                        selectedGroup = groups.first()
                                    }
                                    joinMessage = null
                                    showJoinDialog = false
                                    // Auto-sync to download group's matches and players
                                    app.syncManager.launchDownloadAllFromCloud()
                                } else {
                                    joinMessage = "Invalid invite code. Please check and try again."
                                }
                            } catch (e: Exception) {
                                joinMessage = "Failed to join: ${e.message}"
                            }
                        }
                    },
                    enabled = code.length == 6
                ) {
                    Text("Join Group")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    joinMessage = null
                    showJoinDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            // Show Start Match when the selected group is owned by this user or has temporary scoring access
            val canStartMatch = selectedGroup != null && (
                selectedGroup!!.isOwner || scoringAccessManager.hasTemporaryScoringAccess(selectedGroup!!.id)
            )
            if (canStartMatch) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val intent = Intent(context, TeamSetupActivity::class.java)
                        selectedGroup?.let { intent.putExtra("default_group_id", it.id) }
                        context.startActivity(intent)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Match")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Start Match",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with App Title
            item {
                Column(
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "🏏",
                            fontSize = 32.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Stump'd",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Your Digital Cricket Scorebook",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Stats Card with Group Filter
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickStatItem(
                                value = matchCount.toString(),
                                label = "Matches",
                                icon = Icons.Default.Star
                            )

                            VerticalDivider(
                                modifier = Modifier.height(48.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )

                            QuickStatItem(
                                value = playerCount.toString(),
                                label = "Players",
                                icon = Icons.Default.Person
                            )

                            VerticalDivider(
                                modifier = Modifier.height(48.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )

                            QuickStatItem(
                                value = groups.size.toString(),
                                label = "Groups",
                                icon = Icons.Default.AccountCircle
                            )
                        }

                        // Compact Group Selection (only if groups exist)
                        if (groups.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    onClick = { expanded = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Group:",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = selectedGroup?.name ?: "All Groups",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Select group",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Groups") },
                                        onClick = {
                                            selectedGroup = null
                                            scope.launch { groupRepo.clearDefaultGroupId() }
                                            expanded = false
                                        },
                                        leadingIcon = {
                                            if (selectedGroup == null) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        }
                                    )

                                    HorizontalDivider()

                                    groups.forEach { group ->
                                        DropdownMenuItem(
                                            text = { Text(group.name) },
                                            onClick = {
                                                selectedGroup = group
                                                scope.launch { groupRepo.setDefaultGroupId(group.id) }
                                                expanded = false
                                            },
                                            leadingIcon = {
                                                if (selectedGroup?.id == group.id) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Get Started Card (only shown when no groups exist)
            if (groups.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Get Started with Groups",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a group to organize players, or join an existing group with an invite code",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(Intent(context, GroupManagementActivity::class.java))
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create Group")
                                }
                                Button(
                                    onClick = { showJoinDialog = true }
                                ) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Join Group")
                                }
                            }
                        }
                    }
                }
            }

            // Section: Matches
            item {
                Text(
                    text = "Matches",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuCard(
                        title = "History",
                        icon = Icons.Default.History,
                        description = "View past matches",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(context, MatchHistoryActivity::class.java))
                    }

                    MenuCard(
                        title = "Live Matches",
                        icon = Icons.Default.LiveTv,
                        description = "Watch ongoing",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(context, LiveMatchesActivity::class.java))
                    }
                }
            }

            // Section: Statistics
            item {
                Text(
                    text = "Statistics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuCard(
                        title = "Statistics Hub",
                        icon = Icons.Default.Insights,
                        description = "Stats, rankings, records & more",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        context.startActivity(Intent(context, StatsHubActivity::class.java))
                    }
                }
            }

            // Section: Management
            item {
                Text(
                    text = "Management",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuCard(
                        title = "Players",
                        icon = Icons.Default.Person,
                        description = "Manage players",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(context, AddPlayerActivity::class.java))
                    }

                    MenuCard(
                        title = "Groups",
                        icon = Icons.Default.Group,
                        description = "Manage groups",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(context, GroupManagementActivity::class.java))
                    }
                }
            }

            // Section: Cloud & Data
            item {
                Text(
                    text = "Cloud & Data",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuCard(
                        title = "Cloud Sync",
                        icon = Icons.Default.Cloud,
                        description = "Backup & sync",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(context, EnhancedCloudSyncActivity::class.java))
                    }

                    MenuCard(
                        title = "Data",
                        icon = Icons.Default.Storage,
                        description = "Import & export",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(context, DataManagementActivity::class.java))
                    }
                }
            }

            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuCard(
                        title = "About",
                        icon = Icons.Default.Info,
                        description = "App info & updates",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    }
                }
            }

            // Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "by LogPoseLabs",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun QuickStatItem(
    value: String,
    label: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuCard(
    title: String,
    icon: ImageVector,
    description: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = contentColor
            )

            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * Dialog showing update availability with download progress
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!updateInfo.isForceUpdate && updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        icon = {
            when (updateState) {
                is UpdateState.Downloading -> {
                    CircularProgressIndicator(
                        progress = { updateState.progress / 100f },
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
                    is UpdateState.Downloading -> "Downloading Update..."
                    is UpdateState.ReadyToInstall -> "Ready to Install"
                    is UpdateState.Installing -> "Installing..."
                    is UpdateState.Error -> "Update Failed"
                    else -> if (updateInfo.isForceUpdate) "Update Required" else "Update Available"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (updateState) {
                    is UpdateState.Downloading -> {
                        Text("Downloading version ${updateInfo.latestVersionName}...")
                        LinearProgressIndicator(
                            progress = { updateState.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${updateState.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateState.Error -> {
                        Text(
                            updateState.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        // Version info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Current",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "v${updateInfo.currentVersionName}",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "New",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "v${updateInfo.latestVersionName}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider()

                        Text(
                            updateInfo.updateMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (updateInfo.isForceUpdate) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "This update is required to continue using the app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateState.Downloading -> {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
                is UpdateState.Error -> {
                    Button(onClick = onUpdate) {
                        Text("Retry")
                    }
                }
                is UpdateState.ReadyToInstall, is UpdateState.Installing -> {
                    // No button needed, installation in progress
                }
                else -> {
                    Button(onClick = onUpdate) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Update Now")
                    }
                }
            }
        },
        dismissButton = {
            if (!updateInfo.isForceUpdate && updateState !is UpdateState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    StumpdTheme {
        MainScreen()
    }
}
