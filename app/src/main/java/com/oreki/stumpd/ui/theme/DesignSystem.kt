package com.oreki.stumpd.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

val SuccessContainer = Color(0xFFDCF7E5)
val WarningContainer = Color(0xFFFFF3D6)

@Composable
fun Title(text: String) = Text(
    text = text,
    fontSize = 22.sp,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onBackground
)

@Composable
fun SectionTitle(text: String) = Text(
    text = text,
    fontSize = 16.sp,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary
)

@Composable
fun Label(text: String) = Text(
    text = text,
    fontSize = 12.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun SectionCard(
    title: String? = null,
    sectionContainerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = sectionContainerColor ?: MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                SectionTitle(title)
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun PrimaryCta(
    text: String,
    modifier: Modifier = Modifier,   // default value avoids null issues
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(text)
    }
}

@Composable
fun SecondaryCta(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) { Text(text) }
}
val SectionCardColors @Composable get() =
    CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

@Composable
fun ResultChip(text: String, positive: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (positive) successContainerAdaptive() else MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StumpdTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Label(subtitle)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringTopBar(
    visible: Boolean,
    title: String,
    onBack: () -> Unit,
    onMenu: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        SmallTopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
            },
            // Keep the bar unobtrusive; you can also try Color.Transparent with a scrim if you like
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            // Reserve status bar space so content doesn't overlap the system bar
            windowInsets = WindowInsets.statusBars
        )
    }
}



@Composable
fun cardContainer(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
fun sectionContainer(): Color = MaterialTheme.colorScheme.surfaceContainer

@Composable
fun successContainerAdaptive(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh
    else Color(0xFFDCF7E5) // your SuccessContainer

@Composable
fun warningContainerAdaptive(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh
    else Color(0xFFFFF3D6) // your WarningContainer


@Composable
fun GroupActionsRow(
    onNewGroup: () -> Unit,
    onManageGroups: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = onNewGroup,
            label = { Text("New Group") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
        )
        AssistChip(
            onClick = onManageGroups,
            label = { Text("Manage Groups") },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
        )
    }
}
