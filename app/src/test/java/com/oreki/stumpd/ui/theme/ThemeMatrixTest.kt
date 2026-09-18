package com.oreki.stumpd.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Every palette, in both modes, checked against the things the re-skin now leans on.
 *
 * The UI pass was tuned on Pitch Green in dark mode. There are eight palettes and two modes, and
 * six of those palettes are derived by a hand-rolled HSV ramp — so "looks right here" says very
 * little about the other fifteen combinations. These are the assertions that would have caught
 * the two real colour bugs found by eye during the pass: a container paired with content from a
 * *different* role (trophy-gold text on leather-brown, in every tonal button in the app), and
 * cards whose edge disappeared because the hairline matched the surface it sat on.
 *
 * Thresholds are WCAG contrast ratios, which is the only non-arbitrary yardstick available:
 * 4.5:1 for body text, 3:1 for large text and for a boundary you only need to *see*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeMatrixTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    /** Every (palette, mode) the user can actually end up in, including a custom seed. */
    private fun matrix(): List<Pair<String, ColorScheme>> = buildList {
        StumpdPaletteId.selectable().forEach { palette ->
            listOf(false, true).forEach { dark ->
                val label = "${palette.name}/${if (dark) "dark" else "light"}"
                add(label to colorSchemeFor(palette, dark, null, context))
            }
        }
        // A couple of arbitrary custom seeds, since the user can pick any hue with the wheel.
        listOf(0xFFEC407A.toInt() to "pink", 0xFF9E9D24.toInt() to "olive").forEach { (argb, name) ->
            listOf(false, true).forEach { dark ->
                add("CUSTOM-$name/${if (dark) "dark" else "light"}" to
                    colorSchemeFor(StumpdPaletteId.CUSTOM, dark, argb, context))
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    private fun assertReadable(
        label: String,
        pair: String,
        content: Color,
        container: Color,
        minRatio: Double = 4.5,
    ) {
        val ratio = contrast(content, container)
        assertThat("$label $pair contrast ${"%.2f".format(ratio)}:1 (min $minRatio:1)")
            .isNotNull()
        assertThat(ratio).isAtLeast(minRatio)
    }

    @Test
    fun `text is readable on every container role in every palette and mode`() {
        val failures = mutableListOf<String>()
        matrix().forEach { (label, s) ->
            val pairs = listOf(
                "onSurface/surface" to (s.onSurface to s.surface),
                "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
                "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
                "onPrimary/primary" to (s.onPrimary to s.primary),
                "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                // The pair that was broken: tonal buttons and selected chips take these two,
                // and Pitch Green used to hand them amber-on-brown from unrelated roles.
                "onSecondaryContainer/secondaryContainer" to
                    (s.onSecondaryContainer to s.secondaryContainer),
                "onTertiaryContainer/tertiaryContainer" to
                    (s.onTertiaryContainer to s.tertiaryContainer),
                // The wicket key.
                "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
                "onError/error" to (s.onError to s.error),
            )
            pairs.forEach { (name, colors) ->
                val ratio = contrast(colors.first, colors.second)
                if (ratio < 4.5) failures += "$label $name = ${"%.2f".format(ratio)}:1"
            }
        }
        assertThat(failures).isEmpty()
    }

    @Test
    fun `the card hairline is visible against the card surface everywhere`() {
        // Cards are surfaceContainer with a 1dp outlineVariant edge and no elevation. If those
        // two colours converge the card dissolves into the page — which is exactly what
        // happened in light mode before the edge was added.
        val failures = mutableListOf<String>()
        matrix().forEach { (label, s) ->
            val ratio = contrast(s.outlineVariant, s.surfaceContainer)
            if (ratio < 1.08) failures += "$label outlineVariant/surfaceContainer = ${"%.3f".format(ratio)}:1"
        }
        assertThat(failures).isEmpty()
    }

    @Test
    fun `an accent tint at twelve percent is distinguishable from the surface under it`() {
        // `primary.copy(alpha = 0.12f)` over surfaceContainer is how selected chips, the striker
        // panel, icon badges and the leader row are marked. If the composite is the surface
        // again, every one of those states silently stops reading.
        val failures = mutableListOf<String>()
        matrix().forEach { (label, s) ->
            val tinted = s.primary.copy(alpha = 0.12f).compositeOverOpaque(s.surfaceContainer)
            val delta = kotlin.math.abs(tinted.luminance() - s.surfaceContainer.luminance())
            if (delta < 0.004) failures += "$label tint delta = ${"%.5f".format(delta)}"
        }
        assertThat(failures).isEmpty()
    }

    @Test
    fun `success and warning read against the surfaces they are drawn on`() {
        listOf(false, true).forEach { dark ->
            val stumpd = stumpdColorsFor(dark)
            val scheme = colorSchemeFor(StumpdPaletteId.PITCH_GREEN, dark, null, context)
            assertThat(contrast(stumpd.success, scheme.surface)).isAtLeast(3.0)
            assertThat(contrast(stumpd.warning, scheme.surface)).isAtLeast(3.0)
        }
    }

    /** Source-over composite of a translucent colour onto an opaque one. */
    private fun Color.compositeOverOpaque(background: Color): Color = Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
        alpha = 1f,
    )
}
