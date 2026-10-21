package com.orbit.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbit.app.navigation.OrbitAppShell
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitConfirmationDialog
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitErrorBanner
import com.orbit.core.ui.theme.OrbitTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrbitComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun orbitConfirmationDialog_rendersTitleAndMessage_andFiresCallbacks() {
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            OrbitTheme {
                OrbitConfirmationDialog(
                    title = "Erase Data",
                    message = "Are you sure you want to delete everything?",
                    confirmButtonText = "Delete Permanently",
                    isDestructive = true,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Erase Data").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to delete everything?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete Permanently").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        composeTestRule.onNodeWithText("Delete Permanently").performClick()
        assertThat(confirmed).isTrue()

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun orbitErrorBanner_rendersMessage_andFiresDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            OrbitTheme {
                OrbitErrorBanner(
                    errorMessage = "Integrity verification failed",
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Integrity verification failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dismiss").performClick()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun orbitEmptyState_rendersTitleAndDescription() {
        composeTestRule.setContent {
            OrbitTheme {
                OrbitEmptyState(
                    title = "No Tasks Today",
                    description = "Capture tasks quickly or enjoy your free time."
                )
            }
        }

        composeTestRule.onNodeWithText("No Tasks Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Capture tasks quickly or enjoy your free time.").assertIsDisplayed()
    }

    @Test
    fun orbitButton_rendersText_andFiresOnClick() {
        var clicked = false

        composeTestRule.setContent {
            OrbitTheme {
                OrbitButton(
                    text = "Capture Task",
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Capture Task").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun orbitCard_clickable_firesCallback() {
        var clicked = false

        composeTestRule.setContent {
            OrbitTheme {
                OrbitCard(onClick = { clicked = true }) {
                    Text(text = "Card Content")
                }
            }
        }

        composeTestRule.onNodeWithText("Card Content").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun appShell_rendersBottomNavTabs_andAllowsNavigation() {
        composeTestRule.setContent {
            OrbitTheme {
                OrbitAppShell()
            }
        }

        // Verify bottom nav destinations exist
        composeTestRule.onNodeWithContentDescription("Today").assertExists()
        composeTestRule.onNodeWithContentDescription("Agenda").assertExists()
        composeTestRule.onNodeWithContentDescription("Videos").assertExists()
        composeTestRule.onNodeWithContentDescription("Attention").assertExists()
        composeTestRule.onNodeWithContentDescription("Settings").assertExists()

        // Today screen initial empty state
        composeTestRule.onNodeWithText("All clear for today").assertExists()

        // Navigate to Agenda
        composeTestRule.onNodeWithContentDescription("Agenda").performClick()
        composeTestRule.onNodeWithText("No upcoming events").assertExists()

        // Navigate to Video Inbox
        composeTestRule.onNodeWithContentDescription("Videos").performClick()
        composeTestRule.onNodeWithText("Video queue is empty").assertExists()

        // Navigate to Attention
        composeTestRule.onNodeWithContentDescription("Attention").performClick()
        composeTestRule.onNodeWithText("Attention Tracking").assertExists()

        // Navigate back to Today
        composeTestRule.onNodeWithContentDescription("Today").performClick()
        composeTestRule.onNodeWithText("All clear for today").assertExists()
    }
}
