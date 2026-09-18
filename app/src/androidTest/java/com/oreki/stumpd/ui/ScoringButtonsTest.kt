package com.oreki.stumpd.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Player
import com.oreki.stumpd.ui.scoring.ScoringButtons
import com.oreki.stumpd.ui.theme.StumpdTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ScoringButtonsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun scoringButtonsContent(
        striker: Player? = Player(name = "Striker"),
        nonStriker: Player? = Player(name = "NonStriker"),
        bowler: Player? = Player(name = "Bowler"),
        isInningsComplete: Boolean = false,
        matchSettings: MatchSettings = MatchSettings(shortPitch = false),
        availableBatsmen: Int = 2,
        calculatedTotalRuns: Int = 0,
        onScoreRuns: (Int) -> Unit = {},
        onShowExtras: () -> Unit = {},
        onShowWicket: () -> Unit = {},
        onUndo: () -> Unit = {},
        onWide: () -> Unit = {},
        onRetire: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StumpdTheme {
                ScoringButtons(
                    striker = striker,
                    nonStriker = nonStriker,
                    bowler = bowler,
                    isInningsComplete = isInningsComplete,
                    matchSettings = matchSettings,
                    availableBatsmen = availableBatsmen,
                    calculatedTotalRuns = calculatedTotalRuns,
                    onScoreRuns = onScoreRuns,
                    onShowExtras = onShowExtras,
                    onShowWicket = onShowWicket,
                    onUndo = onUndo,
                    onWide = onWide,
                    onRetire = onRetire,
                )
            }
        }
    }

    @Test
    fun runButtons_zeroThroughSix_renderWhenScoringReadyAndNotShortPitch() {
        scoringButtonsContent(matchSettings = MatchSettings(shortPitch = false))
        for (i in 0..6) {
            composeTestRule.onNodeWithTag("run_$i").assertIsDisplayed()
        }
    }

    @Test
    fun runButtons_clickInvokesOnScoreRunsWithCorrectValues() {
        val scored = mutableListOf<Int>()
        scoringButtonsContent(onScoreRuns = { scored += it })
        composeTestRule.onNodeWithTag("run_3").performClick()
        composeTestRule.onNodeWithTag("run_0").performClick()
        composeTestRule.onNodeWithTag("run_6").performClick()
        assertThat(scored).isEqualTo(listOf(3, 0, 6))
    }

    @Test
    fun wide_extras_wicket_undo_renderAndInvokeCallbacks() {
        var wideClicks = 0
        var extrasClicks = 0
        var wicketClicks = 0
        var undoClicks = 0
        scoringButtonsContent(
            onWide = { wideClicks++ },
            onShowExtras = { extrasClicks++ },
            onShowWicket = { wicketClicks++ },
            onUndo = { undoClicks++ },
        )
        composeTestRule.onNodeWithTag("btn_wide").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_extras").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_wicket").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_undo").assertIsDisplayed().performClick()
        assertThat(wideClicks).isEqualTo(1)
        assertThat(extrasClicks).isEqualTo(1)
        assertThat(wicketClicks).isEqualTo(1)
        assertThat(undoClicks).isEqualTo(1)
    }

    @Test
    fun shortPitch_showsOnlyRunZeroThroughFour() {
        scoringButtonsContent(matchSettings = MatchSettings(shortPitch = true))
        for (i in 0..4) {
            composeTestRule.onNodeWithTag("run_$i").assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("run_5").assertDoesNotExist()
        composeTestRule.onNodeWithTag("run_6").assertDoesNotExist()
    }
}
