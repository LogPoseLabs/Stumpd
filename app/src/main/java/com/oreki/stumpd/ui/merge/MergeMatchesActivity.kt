package com.oreki.stumpd.ui.merge

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.oreki.stumpd.ui.theme.rememberMessenger
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oreki.stumpd.data.local.entity.PlayerEntity
import com.oreki.stumpd.data.sync.MatchGroupAdoption
import com.oreki.stumpd.data.sync.MatchMergeParser
import com.oreki.stumpd.data.sync.MergeSourcePlayer
import com.oreki.stumpd.data.sync.PlayerMergeMapping
import com.oreki.stumpd.data.sync.SyncWorkEntryPoint
import com.oreki.stumpd.data.sync.firebase.FirestoreGroupDao
import com.oreki.stumpd.data.sync.firebase.FirestoreMatchDao
import com.oreki.stumpd.data.util.InviteCodeManager
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.ui.history.rememberGroupRepository
import com.oreki.stumpd.ui.history.rememberMatchRepository
import com.oreki.stumpd.ui.theme.StumpdTheme
import com.oreki.stumpd.ui.theme.StumpdTopBar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MergeStep { Destination, Source, Matches, Mapping }

private sealed class PlayerMapChoice {
    data object Unselected : PlayerMapChoice()
    data object CreateNew : PlayerMapChoice()
    data class Existing(val player: PlayerEntity) : PlayerMapChoice()
}

@AndroidEntryPoint
class MergeMatchesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            StumpdTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MergeMatchesScreen(onClose = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeMatchesScreen(onClose: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = rememberMessenger(snackbarHostState)

    val context = LocalContext.current
    val matchRepo = rememberMatchRepository()
    val groupRepo = rememberGroupRepository()
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(MergeStep.Destination) }
    var ownedGroups by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var destGroup by remember { mutableStateOf<Pair<String, String>?>(null) }
    var destPlayers by remember { mutableStateOf<List<PlayerEntity>>(emptyList()) }
    var inviteCode by remember { mutableStateOf("") }
    var loadedMatches by remember { mutableStateOf<List<MatchHistory>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sourcePlayers by remember { mutableStateOf<List<MergeSourcePlayer>>(emptyList()) }
    var choices by remember { mutableStateOf<Map<String, PlayerMapChoice>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val owned = groupRepo.listGroups().filter { it.isOwner }
        ownedGroups = owned.map { it.id to it.name }
        if (ownedGroups.size == 1) destGroup = ownedGroups.first()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (json.isNullOrBlank()) {
                    status = "Could not read that file."
                } else {
                    val parsed = withContext(Dispatchers.IO) { MatchMergeParser.parseMatchesFromJson(json) }
                    if (parsed.isEmpty()) {
                        status = "No matches found in that file."
                    } else {
                        loadedMatches = parsed
                        selectedIds = parsed.map { it.id }.toSet()
                        step = MergeStep.Matches
                    }
                }
            } catch (e: Exception) {
                status = "Could not parse file: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun loadDestPlayers(groupId: String) {
        scope.launch {
            destPlayers = withContext(Dispatchers.IO) { groupRepo.getMembers(groupId) }
        }
    }

    fun fetchInvite() {
        val code = InviteCodeManager.normalizeCode(inviteCode)
        if (code.length != 6) {
            status = "Enter a 6-character invite code."
            return
        }
        scope.launch {
            busy = true
            status = "Looking up group…"
            try {
                val groupDao = FirestoreGroupDao()
                val matchDao = FirestoreMatchDao()
                val groupData = withContext(Dispatchers.IO) {
                    groupDao.findGroupByInviteCode(code)
                }
                if (groupData == null) {
                    status = "No group found for that code."
                } else {
                    val loaded = withContext(Dispatchers.IO) {
                        val ids = matchDao.listMatchIdsInGroup(groupData.group.id)
                        ids.mapNotNull { matchDao.downloadCompleteMatch(it) }
                    }
                    if (loaded.isEmpty()) {
                        status = "That group has no matches in the cloud."
                    } else {
                        loadedMatches = loaded
                        selectedIds = loaded.map { it.id }.toSet()
                        status = null
                        step = MergeStep.Matches
                    }
                }
            } catch (e: Exception) {
                status = "Failed to load matches: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun goToMapping() {
        val picked = loadedMatches.filter { it.id in selectedIds }
        if (picked.isEmpty()) {
            status = "Select at least one match."
            return
        }
        val dest = destGroup ?: return
        scope.launch {
            busy = true
            try {
                val members = withContext(Dispatchers.IO) { groupRepo.getMembers(dest.first) }
                destPlayers = members
                val players = MatchGroupAdoption.collectSourcePlayers(picked)
                sourcePlayers = players
                choices = players.associate { source ->
                    val auto = members.firstOrNull {
                        MatchGroupAdoption.normalizePlayerName(it.name) == source.normalizedName
                    }
                    source.normalizedName to if (auto != null) {
                        PlayerMapChoice.Existing(auto)
                    } else {
                        PlayerMapChoice.Unselected
                    }
                }
                status = null
                step = MergeStep.Mapping
            } finally {
                busy = false
            }
        }
    }

    fun commitMerge() {
        val dest = destGroup ?: return
        val picked = loadedMatches.filter { it.id in selectedIds }
        val mappings = sourcePlayers.map { source ->
            when (val choice = choices[source.normalizedName] ?: PlayerMapChoice.Unselected) {
                PlayerMapChoice.Unselected -> return@map null
                PlayerMapChoice.CreateNew -> PlayerMergeMapping(
                    sourceNormalizedName = source.normalizedName,
                    createNew = true,
                )
                is PlayerMapChoice.Existing -> PlayerMergeMapping(
                    sourceNormalizedName = source.normalizedName,
                    createNew = false,
                    destinationPlayerId = choice.player.id,
                )
            }
        }
        if (mappings.any { it == null }) {
            status = "Map every player before merging."
            return
        }
        scope.launch {
            busy = true
            status = null
            try {
                val result = matchRepo.mergeMatchesIntoOwnedGroup(
                    destinationGroupId = dest.first,
                    matches = picked,
                    mappings = mappings.filterNotNull(),
                )
                val syncManager = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SyncWorkEntryPoint::class.java,
                ).completeSyncManager()
                syncManager.initialize()
                syncManager.syncIncrementalChanges()
                val msg = buildString {
                    append("Merged ${result.adoptedCount} match(es) into ${dest.second}")
                    if (result.playersAddedToGroup > 0) {
                        append(", added ${result.playersAddedToGroup} player(s)")
                    }
                    if (result.errors.isNotEmpty()) {
                        append("\n${result.errors.size} failed")
                    }
                }
                messenger.show(msg, long = true)
                if (result.adoptedCount > 0 && result.errors.isEmpty()) {
                    onClose()
                } else {
                    status = result.errors.joinToString("\n").ifBlank { msg }
                }
            } catch (e: Exception) {
                status = "Merge failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StumpdTopBar(
                title = "Merge matches",
                subtitle = "Bring a friend's scored match into your group",
                onBack = onClose,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            status?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            when (step) {
                MergeStep.Destination -> {
                    Text("1. Your group (you must own it)", fontWeight = FontWeight.Bold)
                    if (ownedGroups.isEmpty()) {
                        Text("You don't own any groups yet. Create one first.")
                    } else {
                        ownedGroups.forEach { group ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        destGroup = group
                                        loadDestPlayers(group.first)
                                    },
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = destGroup?.first == group.first,
                                        onCheckedChange = {
                                            destGroup = group
                                            loadDestPlayers(group.first)
                                        },
                                    )
                                    Text(group.second, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { step = MergeStep.Source },
                        enabled = destGroup != null && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next") }
                }

                MergeStep.Source -> {
                    Text("2. How did your friend send the match?", fontWeight = FontWeight.Bold)
                    Text(
                        "JSON export from their Settings, or their group invite code. " +
                            "Matches are copied into ${destGroup?.second ?: "your group"} — their group is not kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { filePicker.launch("application/json") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick JSON file") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = {
                            inviteCode = it.filter { c -> c.isLetterOrDigit() }.take(6).uppercase()
                        },
                        label = { Text("Invite code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { fetchInvite() },
                        enabled = !busy && inviteCode.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Loading…" else "Load from invite code") }
                    TextButton(onClick = { step = MergeStep.Destination }, enabled = !busy) {
                        Text("Back")
                    }
                }

                MergeStep.Matches -> {
                    Text("3. Choose matches to bring in", fontWeight = FontWeight.Bold)
                    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                    loadedMatches.forEach { match ->
                        val checked = match.id in selectedIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - match.id else selectedIds + match.id
                                },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + match.id else selectedIds - match.id
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${match.team1Name} vs ${match.team2Name}", fontWeight = FontWeight.Medium)
                                    Text(
                                        "${dateFormat.format(Date(match.matchDate))} · ${match.firstInningsRuns}/${match.firstInningsWickets} & ${match.secondInningsRuns}/${match.secondInningsWickets}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { goToMapping() },
                        enabled = !busy && selectedIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next: map players") }
                    TextButton(onClick = { step = MergeStep.Source }, enabled = !busy) { Text("Back") }
                }

                MergeStep.Mapping -> {
                    Text("4. Map each player to ${destGroup?.second ?: "your group"}", fontWeight = FontWeight.Bold)
                    Text(
                        "Stats are saved with your group's player ids. Exact name matches are pre-selected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sourcePlayers.forEach { source ->
                        PlayerMappingRow(
                            source = source,
                            destPlayers = destPlayers,
                            choice = choices[source.normalizedName] ?: PlayerMapChoice.Unselected,
                            onChoice = { next ->
                                choices = choices + (source.normalizedName to next)
                            },
                        )
                    }
                    val allMapped = sourcePlayers.all {
                        choices[it.normalizedName] !is PlayerMapChoice.Unselected &&
                            choices[it.normalizedName] != null
                    }
                    Button(
                        onClick = { commitMerge() },
                        enabled = !busy && allMapped,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(if (busy) "Merging…" else "Merge into ${destGroup?.second ?: "group"}")
                    }
                    TextButton(onClick = { step = MergeStep.Matches }, enabled = !busy) { Text("Back") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMappingRow(
    source: MergeSourcePlayer,
    destPlayers: List<PlayerEntity>,
    choice: PlayerMapChoice,
    onChoice: (PlayerMapChoice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (choice) {
        PlayerMapChoice.Unselected -> "Choose player"
        PlayerMapChoice.CreateNew -> "Create \"${source.displayName}\" in my group"
        is PlayerMapChoice.Existing -> choice.player.name
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(source.displayName, fontWeight = FontWeight.Medium)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    label = { Text("Maps to") },
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    destPlayers.forEach { player ->
                        DropdownMenuItem(
                            text = { Text(player.name) },
                            onClick = {
                                onChoice(PlayerMapChoice.Existing(player))
                                expanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Create new player in my group") },
                        onClick = {
                            onChoice(PlayerMapChoice.CreateNew)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
