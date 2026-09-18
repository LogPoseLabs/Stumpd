package com.oreki.stumpd.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.ui.theme.EmptyState
import com.oreki.stumpd.ui.theme.PrimaryCta
import com.oreki.stumpd.ui.theme.StumpdTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DesignSystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun primaryCta_rendersLabelAndInvokesClick() {
        var clicks = 0
        composeTestRule.setContent {
            StumpdTheme {
                PrimaryCta(text = "Save match") { clicks++ }
            }
        }
        composeTestRule.onNodeWithText("Save match").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Save match").performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun primaryCta_whenDisabled_isNotEnabled() {
        var clicks = 0
        composeTestRule.setContent {
            StumpdTheme {
                PrimaryCta(text = "Continue", enabled = false) { clicks++ }
            }
        }
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed().assertIsNotEnabled()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun emptyState_rendersIconTitleAndDescription() {
        composeTestRule.setContent {
            StumpdTheme {
                EmptyState(
                    icon = Icons.Filled.Inbox,
                    title = "Nothing here",
                    description = "Add a match to get started.",
                )
            }
        }
        composeTestRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a match to get started.").assertIsDisplayed()
        composeTestRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
            .assertIsDisplayed()
    }
}
