package com.oreki.stumpd.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PalettesTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `schemeFromSeed is deterministic for the same seed and mode`() {
        val first = schemeFromSeed(OCEAN_SEED, dark = false)
        val second = schemeFromSeed(OCEAN_SEED, dark = false)

        assertEquals(first.primary, second.primary)
        assertEquals(first.secondary, second.secondary)
        assertEquals(first.tertiary, second.tertiary)
        assertEquals(first.background, second.background)
    }

    @Test
    fun `schemeFromSeed produces distinct primary secondary and tertiary`() {
        val scheme = schemeFromSeed(PURPLE_SEED, dark = false)

        assertNotEquals(scheme.primary, scheme.secondary)
        assertNotEquals(scheme.primary, scheme.tertiary)
        assertNotEquals(scheme.secondary, scheme.tertiary)
    }

    @Test
    fun `schemeFromSeed produces fully opaque colors`() {
        val scheme = schemeFromSeed(PURPLE_SEED, dark = true)

        listOf(
            scheme.primary,
            scheme.onPrimary,
            scheme.primaryContainer,
            scheme.secondary,
            scheme.tertiary,
            scheme.background,
            scheme.surface
        ).forEach { color ->
            assertEquals(1f, color.alpha, 0.001f)
        }
    }

    @Test
    fun `schemeFromSeed dark mode is darker than light mode`() {
        val light = schemeFromSeed(OCEAN_SEED, dark = false)
        val dark = schemeFromSeed(OCEAN_SEED, dark = true)

        assertTrue(dark.background.luminance() < light.background.luminance())
        assertTrue(dark.surface.luminance() < light.surface.luminance())
    }

    @Test
    fun `every curated palette generates a distinct primary`() {
        val primaries = StumpdPaletteId.entries
            .mapNotNull { it.seed }
            .map { schemeFromSeed(it, dark = false).primary }

        assertEquals(primaries.size, primaries.toSet().size)
    }

    @Test
    fun `colorSchemeFor pitch green returns the hand tuned scheme`() {
        val light = colorSchemeFor(StumpdPaletteId.PITCH_GREEN, false, null, context)
        val dark = colorSchemeFor(StumpdPaletteId.PITCH_GREEN, true, null, context)

        assertEquals(PitchGreenLight.primary, light.primary)
        assertEquals(PitchGreenDark.primary, dark.primary)
    }

    @Test
    fun `colorSchemeFor custom uses the stored seed`() {
        val seed = Color(0xFFAA3377)
        val scheme = colorSchemeFor(StumpdPaletteId.CUSTOM, false, seed.toArgb(), context)

        assertEquals(schemeFromSeed(seed, dark = false).primary, scheme.primary)
    }

    @Test
    fun `colorSchemeFor custom without a stored seed falls back to the brand green`() {
        val scheme = colorSchemeFor(StumpdPaletteId.CUSTOM, false, null, context)
        val fallback = StumpdPaletteId.PITCH_GREEN.seed

        assertNotNull(fallback)
        assertEquals(schemeFromSeed(fallback!!, dark = false).primary, scheme.primary)
    }

    @Test
    fun `fromStorageKey falls back to pitch green for unknown and null keys`() {
        assertEquals(StumpdPaletteId.PITCH_GREEN, StumpdPaletteId.fromStorageKey(null))
        assertEquals(StumpdPaletteId.PITCH_GREEN, StumpdPaletteId.fromStorageKey(""))
        assertEquals(StumpdPaletteId.PITCH_GREEN, StumpdPaletteId.fromStorageKey("RETIRED_PALETTE"))
    }

    @Test
    fun `fromStorageKey round trips every palette`() {
        StumpdPaletteId.entries.forEach { palette ->
            assertEquals(palette, StumpdPaletteId.fromStorageKey(palette.name))
        }
    }

    @Test
    fun `selectable excludes custom because it needs a seed from preferences`() {
        assertFalse(StumpdPaletteId.selectable().contains(StumpdPaletteId.CUSTOM))
        assertTrue(StumpdPaletteId.selectable().contains(StumpdPaletteId.PITCH_GREEN))
    }

    private companion object {
        val OCEAN_SEED = Color(0xFF0277BD)
        val PURPLE_SEED = Color(0xFF7C4DFF)
    }
}
