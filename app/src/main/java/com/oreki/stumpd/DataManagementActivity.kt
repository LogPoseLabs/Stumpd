package com.oreki.stumpd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.oreki.stumpd.ui.theme.StumpdMessenger
import com.oreki.stumpd.ui.theme.rememberMessenger
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.preferences.PasscodeManager
import com.oreki.stumpd.data.preferences.DarkModeOption
import com.oreki.stumpd.data.preferences.ThemePreferencesManager
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.theme.StumpdPaletteId
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.colorSchemeFor
import com.oreki.stumpd.ui.theme.hsvColor
import com.oreki.stumpd.ui.theme.schemeFromSeed
import com.oreki.stumpd.data.sync.SyncWorkEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DataManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            StumpdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DataManagementScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = rememberMessenger(snackbarHostState)

    val context = LocalContext.current
    val repo = rememberMatchRepository()
    val scope = rememberCoroutineScope()
    val groupRepo = rememberGroupRepository()
    
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }
    
    // Group filter state (id to name)
    var availableGroups by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ownedGroups by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var adoptTargetGroup by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isAdopting by remember { mutableStateOf(false) }
    var groupPickerMode by remember { mutableStateOf(GroupPickerMode.Export) }
    
    // Load groups
    LaunchedEffect(Unit) {
        val all = groupRepo.listGroups()
        availableGroups = all.map { it.id to it.name }
        ownedGroups = all.filter { it.isOwner }.map { it.id to it.name }
        if (adoptTargetGroup == null && ownedGroups.size == 1) {
            adoptTargetGroup = ownedGroups.first()
        }
    }
    
    // File picker for import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isImporting = true
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (json != null) {
                        val tempFile = java.io.File(context.cacheDir, "temp_import.json")
                        tempFile.writeText(json)
                        
                        val success = repo.importMatches(tempFile.absolutePath)
                        tempFile.delete()
                        
                        if (success) {
                            messenger.show("✅ Complete backup restored!\nMatches, players, groups & stats imported successfully", long = true)
                        } else {
                            messenger.show("❌ Import failed", long = true)
                        }
                    }
                } catch (e: Exception) {
                    messenger.show("❌ Import error: ${e.message}", long = true)
                } finally {
                    isImporting = false
                }
            }
        }
    }
    
    // Settings is a short menu of categories; each opens in place rather than as a separate
    // activity, so there is no manifest or navigation plumbing and back returns to the menu.
    var category by rememberSaveable { mutableStateOf(SettingsCategory.Menu) }

    // Because the sub-screens are not separate activities there is no back stack entry for
    // them, so system back would close Settings entirely. Intercept it while inside a category
    // and step back to the menu instead, matching the top bar's arrow.
    BackHandler(enabled = category != SettingsCategory.Menu) {
        category = SettingsCategory.Menu
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        category.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (category == SettingsCategory.Menu) {
                            (context as ComponentActivity).finish()
                        } else {
                            category = SettingsCategory.Menu
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (category == SettingsCategory.Menu) {
                SettingsMenu(
                    onSelect = { category = it },
                    onOpenCloudSync = {
                        context.startActivity(Intent(context, EnhancedCloudSyncActivity::class.java))
                    },
                    onOpenAbout = {
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    },
                )
            }

            if (category == SettingsCategory.Appearance) {
                AppearanceSettingsCard()
            }

            if (category == SettingsCategory.Security) {
                SecuritySettingsCard()
                MatchEditLockCard()
            }

            if (category == SettingsCategory.Advanced) {
                AdvancedSettingsCard(messenger)
            }

            if (category == SettingsCategory.Data) {
            // Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📦 Backup & Restore",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Export and import your complete cricket data including matches, players, groups, and statistics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Export Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📤 Export Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Save your data to a backup file in the Downloads folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // Group filter selection
                    if (availableGroups.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                groupPickerMode = GroupPickerMode.Export
                                showGroupPicker = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                selectedGroup?.second ?: "All Groups",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.KeyboardArrowDown, "Select Group", modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    
                    // Export All Data button
                    Button(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                val path = repo.exportMatches()
                                isExporting = false
                                
                                val msg = if (path != null) {
                                    val fileName = java.io.File(path).name
                                    "✅ Complete backup saved!\n$fileName\nin Downloads folder"
                                } else {
                                    "❌ Export failed"
                                }
                                messenger.show(msg, long = true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExporting && !isImporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, "Export")
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isExporting) "Exporting..." else "Export All Data")
                    }
                    
                    // Export Selected Group button (only show if group is selected)
                    if (selectedGroup != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    val path = repo.exportGroupData(selectedGroup!!.first)
                                    isExporting = false
                                    
                                    val msg = if (path != null) {
                                        val fileName = java.io.File(path).name
                                        "✅ ${selectedGroup!!.second} backup saved!\n$fileName\nin Downloads folder"
                                    } else {
                                        "❌ Export failed"
                                    }
                                    messenger.show(msg, long = true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting && !isImporting
                        ) {
                            Icon(Icons.Default.CheckCircle, "Export Group")
                            Spacer(Modifier.width(8.dp))
                            Text("Export ${selectedGroup!!.second} Only", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // Import Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📥 Import Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Restore data from a previously exported backup file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch("application/json") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExporting && !isImporting,
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.MailOutline, "Import")
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isImporting) "Importing..." else "Import Data from File")
                    }
                }
            }

            if (ownedGroups.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Merge a friend's matches into my group",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "If someone scored while you were away, pick their JSON export or invite code, " +
                                "map players to your roster, and save the match with your group's player ids.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(context, com.oreki.stumpd.ui.merge.MergeMatchesActivity::class.java)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Merge matches into my group")
                        }
                    }
                }
            }

            if (ownedGroups.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Assign matches to my group",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Moves matches from other phones or wrong groups into a group you own. " +
                                "Sets group id on each match, remaps player ids to that group's roster (by name), " +
                                "then uploads as group owner when you sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                groupPickerMode = GroupPickerMode.Adopt
                                showGroupPicker = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                adoptTargetGroup?.second ?: "Choose destination group",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.KeyboardArrowDown, "Select group", modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val target = adoptTargetGroup ?: return@Button
                                scope.launch {
                                    isAdopting = true
                                    try {
                                        val result = repo.adoptMatchesIntoOwnedGroup(target.first)
                                        val syncManager = EntryPointAccessors.fromApplication(
                                            context.applicationContext,
                                            SyncWorkEntryPoint::class.java,
                                        ).completeSyncManager()
                                        syncManager.initialize()
                                        syncManager.syncIncrementalChanges()
                                        val msg = buildString {
                                            append("Adopted ${result.adoptedCount} match(es)")
                                            if (result.playersAddedToGroup > 0) {
                                                append(", ${result.playersAddedToGroup} player(s) added to group")
                                            }
                                            if (result.errors.isNotEmpty()) {
                                                append("\n${result.errors.size} failed")
                                            }
                                            append("\nCloud sync attempted.")
                                        }
                                        messenger.show(msg, long = true)
                                    } finally {
                                        isAdopting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAdopting && !isImporting && adoptTargetGroup != null,
                        ) {
                            if (isAdopting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isAdopting) "Working…" else "Adopt all non-group matches")
                        }
                    }
                }
            }
            
            // Warning Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "⚠️ Important",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Importing will merge with existing data\n" +
                        "• Always keep backups before importing\n" +
                        "• Export regularly to avoid data loss",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            } // end Data category
        }
    }

    // Group Picker Dialog
    if (showGroupPicker) {
        val pickerGroups = if (groupPickerMode == GroupPickerMode.Adopt) ownedGroups else availableGroups
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = {
                Text(
                    if (groupPickerMode == GroupPickerMode.Adopt) {
                        "Destination group (you must own it)"
                    } else {
                        "Select Group to Export"
                    },
                )
            },
            text = {
                Column {
                    if (groupPickerMode == GroupPickerMode.Export) {
                        TextButton(
                            onClick = {
                                selectedGroup = null
                                showGroupPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("All Groups", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                    pickerGroups.forEach { group ->
                        TextButton(
                            onClick = {
                                if (groupPickerMode == GroupPickerMode.Adopt) {
                                    adoptTargetGroup = group
                                } else {
                                    selectedGroup = group
                                }
                                showGroupPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(group.second, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private enum class GroupPickerMode {
    Export,
    Adopt,
}

/** Top-level Settings destinations. [Menu] is the category list itself. */
enum class SettingsCategory(val title: String) {
    Menu("Settings"),
    Appearance("Appearance"),
    Security("Security"),
    Data("Data & Backup"),
    Advanced("Advanced"),
}

private data class SettingsEntry(
    val label: String,
    val summary: String,
    val icon: ImageVector,
    val onOpen: () -> Unit,
)

/**
 * The category list. Cloud sync and About already have their own screens, so they are listed
 * here for discoverability and simply launch those activities.
 */
@Composable
private fun SettingsMenu(
    onSelect: (SettingsCategory) -> Unit,
    onOpenCloudSync: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val entries = listOf(
        SettingsEntry(
            "Appearance",
            "Theme colour, palette and dark mode",
            Icons.Default.Palette,
        ) { onSelect(SettingsCategory.Appearance) },
        SettingsEntry(
            "Cloud & Sync",
            "Account, sync status and cloud backup",
            Icons.Default.Cloud,
        ) { onOpenCloudSync() },
        SettingsEntry(
            "Security",
            "Passcodes for deleting and correcting matches",
            Icons.Default.Lock,
        ) { onSelect(SettingsCategory.Security) },
        SettingsEntry(
            "Data & Backup",
            "Export, import, merge and adopt matches",
            Icons.Default.Storage,
        ) { onSelect(SettingsCategory.Data) },
        SettingsEntry(
            "Advanced",
            "Deletions and unlimited undo",
            Icons.Default.Settings,
        ) { onSelect(SettingsCategory.Advanced) },
        SettingsEntry(
            "About",
            "Version, credits and updates",
            Icons.Default.Info,
        ) { onOpenAbout() },
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = entry.onOpen)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            entry.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }

    Text(
        "Match rules (overs, extras, joker) are set per group when you create or edit a group, " +
            "and can be overridden for a single match at setup.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * Palette / dark-mode picker. Writes straight to [ThemePreferencesManager], whose flow every
 * `StumpdTheme` observes, so a tap re-themes all open screens without an activity restart.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeManager = remember { ThemePreferencesManager(context) }
    val settings by ThemePreferencesManager.settingsFlow.collectAsState()
    var showCustomDialog by remember { mutableStateOf(false) }

    val selectedPalette = StumpdPaletteId.fromStorageKey(settings.paletteId)
    // Swatches should preview the mode the user will actually see, not the system's.
    val previewDark = when (settings.darkMode) {
        DarkModeOption.SYSTEM -> isSystemInDarkTheme()
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK -> true
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🎨 Appearance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Changes apply instantly across the app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                "Color palette",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StumpdPaletteId.selectable().forEach { palette ->
                    PaletteSwatch(
                        label = palette.displayName,
                        scheme = colorSchemeFor(palette, previewDark, null, context),
                        selected = palette == selectedPalette,
                        onClick = {
                            scope.launch {
                                themeManager.updateThemeSettings {
                                    it.copy(paletteId = palette.name)
                                }
                            }
                        }
                    )
                }
                PaletteSwatch(
                    label = StumpdPaletteId.CUSTOM.displayName,
                    scheme = colorSchemeFor(
                        StumpdPaletteId.CUSTOM,
                        previewDark,
                        settings.customSeedColorArgb,
                        context
                    ),
                    selected = selectedPalette == StumpdPaletteId.CUSTOM,
                    onClick = { showCustomDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Dark mode",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DarkModeOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = settings.darkMode == option,
                        onClick = {
                            scope.launch {
                                themeManager.updateThemeSettings { it.copy(darkMode = option) }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, DarkModeOption.entries.size)
                    ) {
                        Text(
                            option.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomColorDialog(
            initialSeedArgb = settings.customSeedColorArgb,
            previewDark = previewDark,
            onDismiss = { showCustomDialog = false },
            onApply = { seedArgb ->
                showCustomDialog = false
                scope.launch {
                    themeManager.updateThemeSettings {
                        it.copy(
                            paletteId = StumpdPaletteId.CUSTOM.name,
                            customSeedColorArgb = seedArgb
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun PaletteSwatch(
    label: String,
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(58.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(58.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
            val wedges = listOf(scheme.primary, scheme.secondary, scheme.tertiary)
            val outline = MaterialTheme.colorScheme.outline
            Canvas(modifier = Modifier.size(46.dp)) {
                val sweep = 360f / wedges.size
                wedges.forEachIndexed { index, color ->
                    drawArc(
                        color = color,
                        startAngle = -90f + index * sweep,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                }
                drawCircle(color = outline, style = Stroke(width = 1.dp.toPx()))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            lineHeight = 13.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun CustomColorDialog(
    initialSeedArgb: Int?,
    previewDark: Boolean,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    val initialHsv = remember(initialSeedArgb) {
        val fallback = StumpdPaletteId.PITCH_GREEN.seed ?: Color(0xFF0D7C66)
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(initialSeedArgb ?: fallback.toArgb(), it)
        }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    // schemeFromSeed clamps saturation into this band, so staying inside it keeps the
    // live preview honest — outside it the slider would move with no visible effect.
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0.35f, 0.85f)) }

    val seed = hsvColor(hue, saturation, 0.55f)
    val preview = schemeFromSeed(seed, previewDark)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Custom color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Drag the bar to pick a hue, then set how vivid it should be.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                HueBar(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Vividness",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0.35f..0.85f
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(preview.primary, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Your theme colors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(preview.primary, preview.secondary, preview.tertiary)
                                .forEach { color ->
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(color, RoundedCornerShape(8.dp))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(8.dp)
                                            )
                                    )
                                }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(seed.toArgb()) }) {
                Text("Apply")
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
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueStops = remember { (0..6).map { hsvColor(it * 60f, 1f, 1f) } }

    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange(hueAtX(offset.x, size.width))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onHueChange(hueAtX(change.position.x, size.width))
                }
            }
    ) {
        drawRect(brush = Brush.horizontalGradient(hueStops))
        val thumbRadius = size.height * 0.3f
        val thumbX = ((hue / 360f) * size.width).coerceIn(thumbRadius, size.width - thumbRadius)
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(thumbX, size.height / 2f),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

private fun hueAtX(x: Float, widthPx: Int): Float =
    if (widthPx <= 0) 0f else (x / widthPx * 360f).coerceIn(0f, 359.9f)

@Composable
fun AdvancedSettingsCard(messenger: StumpdMessenger) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deletionsEnabled by remember { 
        mutableStateOf(com.oreki.stumpd.utils.FeatureFlags.isDeletionsEnabled(context)) 
    }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingToggleValue by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "⚙️ Advanced Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Password-protected features",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            
            // Enable Deletions Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable Deletions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (deletionsEnabled) {
                            "Delete buttons are visible for groups and players"
                        } else {
                            "Delete buttons are hidden for safety"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = deletionsEnabled,
                    onCheckedChange = { newValue ->
                        pendingToggleValue = newValue
                        showPasswordDialog = true
                    }
                )
            }
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            
            // Unlimited Undo Info
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ℹ️",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Unlimited Undo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Undo on the scoring screen steps back through every delivery of the " +
                        "innings, including ones scored before the app was last closed. " +
                        "It stops at the start of the second innings. Nothing to configure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
    
    // Password Dialog
    if (showPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                password = ""
                errorMessage = null
            },
            title = { 
                Text(
                    "🔒 Password Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column {
                    Text(
                        "This setting controls whether delete buttons are shown throughout the app. Enter your password to change this setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // The app's single passcode (Settings → Security), not a password of
                        // its own: this used to keep a second one in plain text.
                        scope.launch {
                            val manager = PasscodeManager(context)
                            if (!manager.isSet() || manager.verify(password)) {
                                com.oreki.stumpd.utils.FeatureFlags.setDeletionsEnabled(context, pendingToggleValue)
                                deletionsEnabled = pendingToggleValue
                                showPasswordDialog = false
                                password = ""
                                errorMessage = null
                                messenger.show(if (pendingToggleValue) "✅ Deletions enabled" else "✅ Deletions disabled")
                            } else {
                                errorMessage = "Incorrect passcode"
                            }
                        }
                    },
                    enabled = password.isNotBlank()
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPasswordDialog = false
                        password = ""
                        errorMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Delete protection: an optional passcode asked for before a match can be deleted.
 *
 * It guards against a mis-tap, not against anyone with the phone — see [PasscodeManager].
 */
@Composable
private fun SecuritySettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val passcodeManager = remember { PasscodeManager(context) }

    var lockSet by remember { mutableStateOf<Boolean?>(null) }
    var showSetDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        lockSet = passcodeManager.isSet()
    }

    LaunchedEffect(Unit) { refresh() }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🔒 Passcode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (lockSet) {
                    true -> "Asked for before deleting a match, editing or deleting a " +
                        "player, and changing the deletions setting. Correcting a saved match " +
                        "has its own password, below."
                    false -> "Destructive actions only ask for confirmation."
                    null -> "Checking…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showSetDialog = true },
                    enabled = lockSet != null,
                ) {
                    Text(if (lockSet == true) "Change passcode" else "Set passcode")
                }
                if (lockSet == true) {
                    OutlinedButton(onClick = { showRemoveDialog = true }) {
                        Text("Remove")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Any passcode will do — a single character is fine. There's no way to recover a " +
                    "forgotten one; changing or removing it needs the current one. It guards " +
                    "against mis-taps, not against someone holding your phone.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSetDialog) {
        PasscodeDialog(
            title = if (lockSet == true) "Change passcode" else "Set passcode",
            requireCurrent = lockSet == true,
            confirmLabel = "Save",
            onVerifyCurrent = { passcodeManager.verify(it) },
            onSubmit = { newPasscode ->
                passcodeManager.setPasscode(newPasscode)
                refresh()
            },
            onDismiss = { showSetDialog = false },
        )
    }

    if (showRemoveDialog) {
        PasscodeDialog(
            title = "Remove passcode",
            requireCurrent = true,
            requireNew = false,
            confirmLabel = "Remove",
            onVerifyCurrent = { passcodeManager.verify(it) },
            onSubmit = {
                passcodeManager.clear()
                refresh()
            },
            onDismiss = { showRemoveDialog = false },
        )
    }
}

/**
 * Asks for the current passcode and/or a new one.
 *
 * [onSubmit] runs only once the current passcode has been checked, so callers don't have to
 * re-verify.
 */
/**
 * The password for correcting a finished match.
 *
 * Separate from the app passcode because the two protect different kinds of damage: deleting a
 * match removes a row, while correcting one rewrites a result, the players' career figures and
 * the group's rankings — and it syncs to everyone else silently. Whoever scores on the day can
 * be trusted with the first without being handed the second.
 *
 * Forgetting it isn't fatal: it can be reset with the app passcode. That's a deliberate weakening
 * — the app passcode already permits deleting the whole match, which is worse than editing it —
 * and it beats the alternative, where a forgotten password freezes a wrong scorecard forever.
 */
@Composable
private fun MatchEditLockCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editLock = remember { PasscodeManager.matchEditLock(context) }
    val appLock = remember { PasscodeManager.appLock(context) }

    var editLockSet by remember { mutableStateOf<Boolean?>(null) }
    var appLockSet by remember { mutableStateOf<Boolean?>(null) }
    var showSetDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        editLockSet = editLock.isSet()
        appLockSet = appLock.isSet()
    }

    LaunchedEffect(Unit) { refresh() }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "✏️ Match editing password",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (editLockSet) {
                    true -> "Asked for before correcting a saved match — a wicket given to the " +
                        "wrong batter, an over credited to the wrong bowler, a substitution or a " +
                        "single ball."
                    false -> "Set one to protect corrections to saved matches. A correction " +
                        "changes results and career figures, and syncs to the rest of the group."
                    null -> "Checking…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showSetDialog = true },
                    enabled = editLockSet != null,
                ) {
                    Text(if (editLockSet == true) "Change" else "Set password")
                }
                if (editLockSet == true) {
                    OutlinedButton(onClick = { showRemoveDialog = true }) { Text("Remove") }
                }
            }

            if (editLockSet == true && appLockSet == true) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showResetDialog = true }) {
                    Text("Forgotten it? Reset with your app passcode")
                }
            }
        }
    }

    if (showSetDialog) {
        PasscodeDialog(
            title = if (editLockSet == true) "Change match editing password" else "Set match editing password",
            requireCurrent = editLockSet == true,
            confirmLabel = "Save",
            onVerifyCurrent = { editLock.verify(it) },
            onSubmit = { editLock.setPasscode(it) },
            onDismiss = {
                showSetDialog = false
                scope.launch { refresh() }
            },
        )
    }

    if (showResetDialog) {
        PasscodeDialog(
            title = "Reset match editing password",
            requireCurrent = true,
            confirmLabel = "Reset",
            currentLabel = "App passcode",
            onVerifyCurrent = { appLock.verify(it) },
            onSubmit = { editLock.setPasscode(it) },
            onDismiss = {
                showResetDialog = false
                scope.launch { refresh() }
            },
        )
    }

    if (showRemoveDialog) {
        PasscodeDialog(
            title = "Remove match editing password",
            requireCurrent = true,
            confirmLabel = "Remove",
            requireNew = false,
            onVerifyCurrent = { editLock.verify(it) },
            onSubmit = { editLock.clear() },
            onDismiss = {
                showRemoveDialog = false
                scope.launch { refresh() }
            },
        )
    }
}

@Composable
private fun PasscodeDialog(
    title: String,
    requireCurrent: Boolean,
    confirmLabel: String,
    onVerifyCurrent: suspend (String) -> Boolean,
    onSubmit: suspend (String) -> Unit,
    onDismiss: () -> Unit,
    requireNew: Boolean = true,
    /** Which secret is being asked for — the reset flow wants the *app* passcode. */
    currentLabel: String = "Current passcode",
    body: String? = null,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val newIsValid = !requireNew || (new.isNotEmpty() && new == repeat)
    val currentIsValid = !requireCurrent || current.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (requireCurrent) {
                    PasscodeField(
                        value = current,
                        onValueChange = { current = it; error = null },
                        label = currentLabel,
                    )
                }
                if (requireNew) {
                    PasscodeField(
                        value = new,
                        onValueChange = { new = it; error = null },
                        label = "New passcode",
                    )
                    PasscodeField(
                        value = repeat,
                        onValueChange = { repeat = it; error = null },
                        label = "Repeat new passcode",
                        isError = repeat.isNotEmpty() && repeat != new,
                    )
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && currentIsValid && newIsValid,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = !requireCurrent || onVerifyCurrent(current)
                        if (ok) {
                            onSubmit(new)
                            busy = false
                            onDismiss()
                        } else {
                            busy = false
                            error = "That passcode doesn't match"
                        }
                    }
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PasscodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
