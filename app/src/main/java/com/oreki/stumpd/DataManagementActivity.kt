package com.oreki.stumpd

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import kotlinx.coroutines.launch

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
    
    // Load groups
    LaunchedEffect(Unit) {
        availableGroups = groupRepo.listGroups().map { 
            it.id to it.name
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
                            Toast.makeText(
                                context,
                                "✅ Complete backup restored!\nMatches, players, groups & stats imported successfully",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "❌ Import failed", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ Import error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isImporting = false
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as ComponentActivity).finish() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            // Advanced Settings Section
            AdvancedSettingsCard()
            
            // Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📦 Backup & Restore",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Export and import your complete cricket data including matches, players, groups, and statistics.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Export Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📤 Export Data",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Save your data to a backup file in the Downloads folder.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    // Group filter selection
                    if (availableGroups.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showGroupPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                selectedGroup?.second ?: "All Groups",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
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
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting && !isImporting
                        ) {
                            Icon(Icons.Default.CheckCircle, "Export Group")
                            Spacer(Modifier.width(8.dp))
                            Text("Export ${selectedGroup!!.second} Only", fontSize = 13.sp)
                        }
                    }
                }
            }
            
            // Import Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📥 Import Data",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Restore data from a previously exported backup file.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    Button(
                        onClick = { filePickerLauncher.launch("application/json") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExporting && !isImporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiary
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
            
            // Warning Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "⚠️ Important",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Importing will merge with existing data\n" +
                        "• Always keep backups before importing\n" +
                        "• Export regularly to avoid data loss",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
    
    // Group Picker Dialog
    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("Select Group to Export") },
            text = {
                Column {
                    // "All Groups" option
                    TextButton(
                        onClick = {
                            selectedGroup = null
                            showGroupPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("All Groups", fontSize = 14.sp)
                    }
                    
                    HorizontalDivider()
                    
                    // Individual group options
                    availableGroups.forEach { group ->
                        TextButton(
                            onClick = {
                                selectedGroup = group
                                showGroupPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(group.second, fontSize = 14.sp)
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

@Composable
fun AdvancedSettingsCard() {
    val context = LocalContext.current
    var deletionsEnabled by remember { 
        mutableStateOf(com.oreki.stumpd.utils.FeatureFlags.isDeletionsEnabled(context)) 
    }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingToggleValue by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Password-protected features",
                        fontSize = 12.sp,
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
                        fontSize = 14.sp,
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
                        fontSize = 12.sp,
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
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Unlimited Undo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Control unlimited undo during live scoring from the Scoring screen. Password required to toggle.",
                    fontSize = 12.sp,
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column {
                    Text(
                        "This setting controls whether delete buttons are shown throughout the app. Enter your password to change this setting.",
                        fontSize = 13.sp,
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
                        // Check password (using the same password from player deletion)
                        val prefs = context.getSharedPreferences("stumpd_prefs", android.content.Context.MODE_PRIVATE)
                        val savedPassword = prefs.getString("deletion_password", null)
                        
                        if (savedPassword == null) {
                            // No password set, save this one
                            prefs.edit().putString("deletion_password", password).apply()
                            com.oreki.stumpd.utils.FeatureFlags.setDeletionsEnabled(context, pendingToggleValue)
                            deletionsEnabled = pendingToggleValue
                            showPasswordDialog = false
                            password = ""
                            errorMessage = null
                            Toast.makeText(
                                context, 
                                if (pendingToggleValue) "✅ Deletions enabled" else "✅ Deletions disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (password == savedPassword) {
                            // Correct password
                            com.oreki.stumpd.utils.FeatureFlags.setDeletionsEnabled(context, pendingToggleValue)
                            deletionsEnabled = pendingToggleValue
                            showPasswordDialog = false
                            password = ""
                            errorMessage = null
                            Toast.makeText(
                                context, 
                                if (pendingToggleValue) "✅ Deletions enabled" else "✅ Deletions disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Wrong password
                            errorMessage = "❌ Incorrect password"
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

