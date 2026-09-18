package com.oreki.stumpd.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextOverflow


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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, // allow wrapping
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
fun StatsTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    LargeTopAppBar(   // 👈 instead of TopAppBar
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

@Composable
fun sectionContainer(): Color = MaterialTheme.colorScheme.surfaceContainer

/**
 * The standard card edge: one device pixel of [outlineVariant].
 *
 * Cards in this app sit on `surfaceContainer` with no elevation. That reads in dark mode, where
 * the container is clearly lighter than the background, but in light mode the two are a hair
 * apart and the cards dissolve into the page. A hairline states the boundary in both modes, and
 * unlike an elevation shadow it survives a flat palette.
 */
@Composable
fun hairline(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)





/**
 * Gradient banner for the top of high-traffic screens.
 *
 * [shape] defaults to rounded-bottom-only, which suits an edge-to-edge header sitting
 * directly under a top bar. Pass a fully rounded shape when the header is inset by the
 * parent's horizontal padding, otherwise the square top corners look clipped.
 */
@Composable
fun GradientHeroHeader(
    title: String,
    subtitle: String? = null,
    emoji: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                // Container roles, not primary/tertiary themselves: those are the *light* end of
                // the ramp in a dark scheme, so the old gradient turned the header into the
                // brightest block on a dark screen. The container pair tracks the mode — dark
                // and rich in dark mode, soft and bright in light mode — with the same hue shift.
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Column {
            if (emoji != null) {
                Text(text = emoji, fontSize = 32.sp)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Shrinks a tappable surface slightly while pressed.
 * Pass the same [interactionSource] to the clickable component, otherwise it never
 * observes presses and the scale stays at 1f.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// Empty State Component
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionButton: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(24.dp))
            actionButton()
        }
    }
}
