package com.oreki.stumpd.data.preferences

import com.oreki.stumpd.ui.theme.StumpdPaletteId
import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure parts of the theme settings model. The DataStore round trip is left to
 * instrumented tests — a JVM test would exercise Robolectric's filesystem rather than
 * anything meaningful about the manager.
 */
class ThemePreferencesManagerTest {

    @Test
    fun `defaults keep the brand palette and follow the system dark mode`() {
        val defaults = ThemeSettings()

        assertEquals(StumpdPaletteId.PITCH_GREEN.name, defaults.paletteId)
        assertEquals(DarkModeOption.SYSTEM, defaults.darkMode)
        assertNull(defaults.customSeedColorArgb)
    }

    @Test
    fun `dark mode options survive a name round trip`() {
        DarkModeOption.entries.forEach { option ->
            assertEquals(option, DarkModeOption.valueOf(option.name))
        }
    }
}
