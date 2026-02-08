package com.oreki.stumpd

import com.oreki.stumpd.domain.model.*
import android.os.Bundle
import androidx.compose.material3.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.oreki.stumpd.utils.FeatureFlags
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import com.oreki.stumpd.data.mappers.toDomain
import com.oreki.stumpd.data.mappers.toEntityWithId
import com.oreki.stumpd.data.sync.firebase.FirestoreGroupDao
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberPlayerRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity


class GroupManagementActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            StumpdTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GroupManagementScreen()
                }
            }
        }
    }
}

@Composable
fun GroupManagementScreen() {
    val context = LocalContext.current
    val groupRepo = rememberGroupRepository()
    val playerRepo = rememberPlayerRepository()

    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<PlayerGroup>>(emptyList()) }
    var inviteCodes by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var claimCodes by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var groupOwnership by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showClaimDialog by remember { mutableStateOf<String?>(null) } // Group ID to claim
    var showRecoveryCodeDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // (groupName, claimCode)
    var memberCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var unavailableCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun refreshData() {
        scope.launch {
            // Build rows with real counts
            val triples = groupRepo.listGroupSummaries()
            groups = triples.map { (g, d, _) -> 
                val unavailableIds = groupRepo.getUnavailablePlayerIds(g.id)
                g.toDomain(d, emptyList(), unavailableIds)
            }
            memberCounts = triples.associate { (g, _, c) -> g.id to c }
            // Get unavailable counts for each group
            unavailableCounts = groups.associate { g -> 
                g.id to g.unavailablePlayerIds.size 
            }
            // Load invite codes and claim codes for owned groups
            inviteCodes = groups.associate { g ->
                g.id to groupRepo.getInviteCode(g.id)
            }
            claimCodes = groups.associate { g ->
                g.id to groupRepo.getClaimCode(g.id)
            }
            // Track ownership
            groupOwnership = triples.associate { (g, _, _) ->
                g.id to g.isOwner
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        refreshData()
    }
    
    // Show snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }
    
    // Refresh when screen resumes (coming back from EditGroupActivity)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = { StumpdTopBar(title = "Groups", subtitle = "Create and manage groups", onBack = { (context as ComponentActivity).finish() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Join Group FAB (smaller)
                SmallFloatingActionButton(
                    onClick = { showJoinDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) { 
                    Icon(Icons.Default.GroupAdd, contentDescription = "Join Group") 
                }
                // Create Group FAB (main)
                FloatingActionButton(onClick = { 
                    val intent = android.content.Intent(context, EditGroupActivity::class.java)
                    context.startActivity(intent)
                }) { 
                    Icon(Icons.Default.Add, contentDescription = "Create Group") 
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups.size) { idx ->
                val g = groups[idx]
                val totalCount = memberCounts[g.id] ?: 0
                val unavailableCount = unavailableCounts[g.id] ?: 0
                val availableCount = totalCount - unavailableCount
                val inviteCode = inviteCodes[g.id]
                val claimCode = claimCodes[g.id]
                val isOwner = groupOwnership[g.id] ?: true
                
                GroupCard(
                    group = g,
                    totalMembers = totalCount,
                    availableMembers = availableCount,
                    inviteCode = inviteCode,
                    claimCode = claimCode,
                    isOwner = isOwner,
                    onEdit = {
                        val intent = android.content.Intent(context, EditGroupActivity::class.java)
                        intent.putExtra("group_id", g.id)
                        context.startActivity(intent)
                    },
                    onGenerateCode = {
                        scope.launch {
                            val code = groupRepo.getOrCreateInviteCode(g.id)
                            inviteCodes = inviteCodes + (g.id to code)
                            // Also generate claim code if not exists
                            val recoveryCode = groupRepo.getOrCreateClaimCode(g.id)
                            if (recoveryCode != null) {
                                claimCodes = claimCodes + (g.id to recoveryCode)
                            }
                            snackbarMessage = "Invite code: $code"
                        }
                    },
                    onShareCode = { code ->
                        // Share via Android share sheet
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, 
                                "Join my cricket group \"${g.name}\" on Stumpd!\n\nInvite Code: $code")
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Invite Code"))
                    },
                    onShowRecoveryCode = { recoveryCode ->
                        // Require biometric/device authentication before showing recovery code
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            authenticateToViewRecoveryCode(activity) {
                                showRecoveryCodeDialog = g.name to recoveryCode
                            }
                        }
                    },
                    onClaimOwnership = {
                        showClaimDialog = g.id
                    }
                )
            }
        }
    }

    if (showCreate) {
        CreateOrEditGroupDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, defaults ->
                scope.launch {
                    groupRepo.createGroup(name, defaults)
                    refreshData()
                    showCreate = false
                }
            }
        )
    }
    
    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                scope.launch {
                    // Check if already joined locally
                    if (groupRepo.hasJoinedGroupWithCode(code)) {
                        snackbarMessage = "You've already joined this group"
                        showJoinDialog = false
                        return@launch
                    }
                    
                    // Get current user ID
                    val app = context.applicationContext as StumpdApplication
                    val userId = app.syncManager.getUserId()
                    
                    if (userId == null) {
                        snackbarMessage = "Please wait for sync to initialize..."
                        showJoinDialog = false
                        return@launch
                    }
                    
                    // Try to join the group via Firestore
                    snackbarMessage = "Looking for group..."
                    
                    try {
                        val firestoreGroupDao = FirestoreGroupDao()
                        val groupData = withContext(Dispatchers.IO) {
                            firestoreGroupDao.joinGroupWithInviteCode(code, userId)
                        }
                        
                        if (groupData != null) {
                            // Save joined group locally (invite code record)
                            groupRepo.joinGroupWithCode(
                                inviteCode = code,
                                remoteGroupId = groupData.group.id,
                                groupName = groupData.group.name
                            )
                            // Also save the full group data so it shows immediately
                            withContext(Dispatchers.IO) {
                                val db = (context.applicationContext as StumpdApplication).database
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
                            snackbarMessage = "Joined \"${groupData.group.name}\" successfully!"
                            refreshTrigger++ // Refresh the list
                            // Auto-sync to download group's matches and players
                            (context.applicationContext as StumpdApplication).syncManager.launchDownloadAllFromCloud()
                        } else {
                            snackbarMessage = "Invalid invite code. Please check and try again."
                        }
                    } catch (e: Exception) {
                        snackbarMessage = "Failed to join group: ${e.message}"
                    }
                    
                    showJoinDialog = false
                }
            }
        )
    }
    
    // Recovery Code Dialog (for owners to view their recovery code)
    showRecoveryCodeDialog?.let { (groupName, recoveryCode) ->
        RecoveryCodeDialog(
            groupName = groupName,
            recoveryCode = recoveryCode,
            onDismiss = { showRecoveryCodeDialog = null }
        )
    }
    
    // Claim Ownership Dialog (for non-owners to claim with recovery code)
    showClaimDialog?.let { groupId ->
        val groupName = groups.find { it.id == groupId }?.name ?: "Group"
        ClaimOwnershipDialog(
            groupName = groupName,
            onDismiss = { showClaimDialog = null },
            onClaim = { recoveryCode ->
                scope.launch {
                    // Get current user ID
                    val app = context.applicationContext as StumpdApplication
                    val userId = app.syncManager.getUserId()
                    
                    if (userId == null) {
                        snackbarMessage = "Please wait for sync to initialize..."
                        return@launch
                    }
                    
                    try {
                        val normalizedCode = com.oreki.stumpd.data.util.InviteCodeManager.normalizeClaimCode(recoveryCode)
                        val firestoreGroupDao = FirestoreGroupDao()
                        val success = withContext(Dispatchers.IO) {
                            firestoreGroupDao.claimOwnership(groupId, normalizedCode, userId)
                        }
                        
                        if (success) {
                            // Update local ownership via repository
                            withContext(Dispatchers.IO) {
                                groupRepo.claimLocalOwnership(groupId, normalizedCode)
                            }
                            snackbarMessage = "You are now the owner of \"$groupName\"!"
                            refreshTrigger++
                        } else {
                            snackbarMessage = "Invalid recovery code. Please check and try again."
                        }
                    } catch (e: Exception) {
                        snackbarMessage = "Failed to claim ownership: ${e.message}"
                    }
                    
                    showClaimDialog = null
                }
            }
        )
    }
}

/**
 * Requires biometric (fingerprint/face) or device credential (PIN/pattern)
 * authentication before showing the recovery code.
 * This prevents someone borrowing the phone from viewing the code.
 */
private fun authenticateToViewRecoveryCode(
    activity: FragmentActivity,
    onSuccess: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)

    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )

    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        // No biometric or device credential available -- show the code anyway
        // (device has no lock screen set up, so no way to protect it further)
        onSuccess()
        return
    }

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // User cancelled or error -- don't show the code
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            // Biometric didn't match -- prompt stays open for retry
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Verify identity")
        .setSubtitle("Authenticate to view recovery code")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}

@Composable
private fun GroupCard(
    group: PlayerGroup, 
    totalMembers: Int, 
    availableMembers: Int,
    inviteCode: String? = null,
    claimCode: String? = null,
    isOwner: Boolean = true,
    onEdit: () -> Unit, 
    onDelete: (() -> Unit)? = null,
    onGenerateCode: (() -> Unit)? = null,
    onShareCode: ((String) -> Unit)? = null,
    onShowRecoveryCode: ((String) -> Unit)? = null,
    onClaimOwnership: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.name, 
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        group.defaults.groundName.ifEmpty { "No ground set" },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            
            // Stats Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total Members
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$totalMembers",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "Total Members",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                VerticalDivider(Modifier.height(48.dp))
                
                // Available for Selection
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (availableMembers > 0) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$availableMembers",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (availableMembers > 0) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "Available",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Invite Code Section
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Invite Code",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (inviteCode != null) {
                        Text(
                            com.oreki.stumpd.data.util.InviteCodeManager.formatForDisplay(inviteCode),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                    } else {
                        Text(
                            "Not generated",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isOwner && inviteCode == null && onGenerateCode != null) {
                        FilledTonalIconButton(onClick = onGenerateCode) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Generate Code",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (inviteCode != null && onShareCode != null) {
                        FilledTonalIconButton(onClick = { onShareCode(inviteCode) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Code",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // Recovery Code Section (only for owners)
            if (isOwner && claimCode != null && onShowRecoveryCode != null) {
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Recovery Code",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap to view (keep safe!)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    
                    FilledTonalIconButton(
                        onClick = { onShowRecoveryCode(claimCode) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "View Recovery Code",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Claim Ownership Section (only for non-owners)
            if (!isOwner && onClaimOwnership != null) {
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Not the owner",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Have the recovery code?",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    
                    FilledTonalButton(
                        onClick = onClaimOwnership,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Claim", fontSize = 13.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            
            // Action Buttons (only for owners)
            if (isOwner) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onEdit,
                        modifier = if (FeatureFlags.isDeletionsEnabled(LocalContext.current) && onDelete != null) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Group")
                    }
                    
                    // Only show delete button if feature flag is enabled
                    if (FeatureFlags.isDeletionsEnabled(LocalContext.current) && onDelete != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(0.5f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // Non-owner info text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Only the group owner can edit this group",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        // Only allow alphanumeric, max 6 characters
                        val filtered = it.filter { c -> c.isLetterOrDigit() }
                            .take(6)
                            .uppercase()
                        code = filtered
                    },
                    label = { Text("Invite Code") },
                    placeholder = { Text("ABC 123") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 24.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.length == 6
            ) {
                Text("Join Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateOrEditGroupDialog(
    initial: PlayerGroup? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, GroupDefaultSettings) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var ground by remember { mutableStateOf(initial?.defaults?.groundName ?: "") }
    var format by remember { mutableStateOf(initial?.defaults?.format ?: BallFormat.WHITE_BALL) }
    var shortPitch by remember { mutableStateOf(initial?.defaults?.shortPitch ?: false) }
    val context = LocalContext.current
    val defaultMatchSettings = remember { MatchSettingsManager(context).getDefaultMatchSettings() }
    var matchSettings by remember { mutableStateOf(initial?.defaults?.matchSettings ?: defaultMatchSettings) }

    var totalOversText by remember(matchSettings.totalOvers) { mutableStateOf(matchSettings.totalOvers.toString()) }
    var maxPerBowlerText by remember(matchSettings.maxOversPerBowler, matchSettings.totalOvers) {
        mutableStateOf(matchSettings.maxOversPerBowler.toString())
    }
    var powerplayOversText by remember(matchSettings.powerplayOvers) {
        mutableStateOf(matchSettings.powerplayOvers.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Group" else "Edit Group") },
        text = {
            // Bounded height so actions remain visible and content scrolls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp) // tune for device density
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ground, onValueChange = { ground = it }, label = { Text("Ground name") })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Format")
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = format == BallFormat.WHITE_BALL,
                        onClick = { format = BallFormat.WHITE_BALL },
                        label = { Text("Limited Overs") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = format == BallFormat.RED_BALL,
                        onClick = { format = BallFormat.RED_BALL },
                        label = { Text("Test") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Short pitch")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = shortPitch, onCheckedChange = { shortPitch = it })
                }

                Spacer(Modifier.height(12.dp))
                Text("Default match settings", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)

                SettingRowMini(
                    label = "Total overs",
                    value = totalOversText,
                    keyboard = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    onValueChange = { v ->
                        // keep UI responsive
                        totalOversText = v.filter { it.isDigit() }.take(2) // e.g., cap to 2 digits if desired
                        val ov = totalOversText.toIntOrNull()
                        if (ov != null && ov in 1..50) {
                            if (matchSettings.totalOvers != ov) {
                                matchSettings = matchSettings.copy(totalOvers = ov)
                                // clamp max/bowler if needed and sync its text mirror
                                if (matchSettings.maxOversPerBowler > ov) {
                                    matchSettings = matchSettings.copy(maxOversPerBowler = ov)
                                    maxPerBowlerText = ov.toString()
                                }
                            }
                        }
                    }
                )

                SettingRowMini(
                    label = "Max/ bowler",
                    value = maxPerBowlerText,
                    keyboard = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    onValueChange = { v ->
                        maxPerBowlerText = v.filter { it.isDigit() }.take(2)
                        val ov = maxPerBowlerText.toIntOrNull()
                        val cap = matchSettings.totalOvers
                        if (ov != null && ov in 1..cap) {
                            if (matchSettings.maxOversPerBowler != ov) {
                                matchSettings = matchSettings.copy(maxOversPerBowler = ov)
                            }
                        }
                    }
                )

                ZeroOneSegment(
                    label = "No ball (+)",
                    selected = matchSettings.noballRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(noballRuns = r) }
                )

                ZeroOneSegment(
                    label = "Wide (+)",
                    selected = matchSettings.wideRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(wideRuns = r) }
                )

                ZeroOneSegment(
                    label = "Bye (+)",
                    selected = matchSettings.byeRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(byeRuns = r) }
                )

                ZeroOneSegment(
                    label = "Leg bye (+)",
                    selected = matchSettings.legByeRuns,
                    onSelect = { r -> matchSettings = matchSettings.copy(legByeRuns = r) }
                )
                
                Spacer(Modifier.height(8.dp))
                SettingRowMini(
                    label = "Powerplay overs",
                    value = powerplayOversText,
                    onValueChange = { v ->
                        powerplayOversText = v
                        v.toIntOrNull()?.let { o ->
                            if (o >= 0 && o <= matchSettings.totalOvers) {
                                matchSettings = matchSettings.copy(powerplayOvers = o)
                            }
                        }
                    }
                )
                
                if (matchSettings.powerplayOvers > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Double runs in powerplay", fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = matchSettings.doubleRunsInPowerplay,
                            onCheckedChange = { matchSettings = matchSettings.copy(doubleRunsInPowerplay = it) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    name.trim(),
                    GroupDefaultSettings(
                        matchSettings = matchSettings.copy(shortPitch = shortPitch),
                        groundName = ground.trim(),
                        format = format.toString(),
                        shortPitch = shortPitch
                    )
                )
            }, enabled = name.isNotBlank()) { Text(if (initial == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZeroOneSegment(
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow {
            listOf(0, 1).forEachIndexed { index, value ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    label = { Text(value.toString()) }
                )
            }
        }
    }
}


@Composable
private fun SettingRowMini(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardOptions = KeyboardOptions.Default
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboard,
            modifier = Modifier.width(96.dp)
        )
    }
    Spacer(Modifier.height(6.dp))
}

/**
 * Dialog to display the recovery code for group owners
 */
@Composable
private fun RecoveryCodeDialog(
    groupName: String,
    recoveryCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formattedCode = com.oreki.stumpd.data.util.InviteCodeManager.formatClaimCodeForDisplay(recoveryCode)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { 
            Icon(
                Icons.Default.Lock, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            ) 
        },
        title = { Text("Recovery Code") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Keep this code safe! You can use it to reclaim ownership of \"$groupName\" if you lose access to this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            formattedCode,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            letterSpacing = 2.sp
                        )
                    }
                }
                
                Text(
                    "⚠️ Do NOT share this code publicly. Anyone with this code can claim ownership of your group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        // Copy to clipboard
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
                            as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Recovery Code", recoveryCode)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    )
}

/**
 * Dialog for non-owners to claim ownership with a recovery code
 */
@Composable
private fun ClaimOwnershipDialog(
    groupName: String,
    onDismiss: () -> Unit,
    onClaim: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { 
            Icon(
                Icons.Default.Person, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            ) 
        },
        title = { Text("Claim Ownership") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the 12-character recovery code to claim ownership of \"$groupName\":",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "The previous owner should have shared this code with you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { 
                        // Allow alphanumeric and dashes, max 14 characters (12 + 2 dashes)
                        val filtered = it.filter { c -> c.isLetterOrDigit() || c == '-' }
                            .take(14)
                            .uppercase()
                        code = filtered
                    },
                    label = { Text("Recovery Code") },
                    placeholder = { Text("XXXX-XXXX-XXXX") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 18.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onClaim(code) },
                enabled = code.replace("-", "").length == 12
            ) {
                Text("Claim Ownership")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

