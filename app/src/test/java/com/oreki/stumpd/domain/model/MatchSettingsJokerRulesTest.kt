package com.oreki.stumpd.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSettingsJokerRulesTest {

    private val gson = Gson()

    @Test
    fun `joker can bowl by default`() {
        assertTrue(MatchSettings().jokerCanBowl)
    }

    @Test
    fun `settings saved before the joker bowling option still allow bowling`() {
        // The critical compatibility case: every group default and completed match already on
        // disk was serialized without jokerCanBowl. If Gson left it at the JVM default (false)
        // the joker would silently stop being able to bowl in existing matches.
        val legacyJson = """
            {
              "totalOvers": 8,
              "maxPlayersPerTeam": 11,
              "maxOversPerBowler": 2,
              "jokerCanBatAndBowl": true,
              "jokerMaxOvers": 1,
              "shortPitch": true
            }
        """.trimIndent()

        val settings = gson.fromJson(legacyJson, MatchSettings::class.java)

        assertTrue("legacy settings must keep joker bowling enabled", settings.jokerCanBowl)
        assertEquals(8, settings.totalOvers)
        assertEquals(2, settings.maxOversPerBowler)
    }

    @Test
    fun `disabling joker bowling survives a json round trip`() {
        val settings = MatchSettings(jokerCanBowl = false, jokerMaxOvers = 3)

        val restored = gson.fromJson(gson.toJson(settings), MatchSettings::class.java)

        assertFalse(restored.jokerCanBowl)
        assertEquals(3, restored.jokerMaxOvers)
    }

    @Test
    fun `joker bowling is independent of the joker master switch`() {
        // A joker who bats but never bowls is the whole point of the option.
        val batOnlyJoker = MatchSettings(jokerCanBatAndBowl = true, jokerCanBowl = false)

        assertTrue(batOnlyJoker.jokerCanBatAndBowl)
        assertFalse(batOnlyJoker.jokerCanBowl)
    }
}
