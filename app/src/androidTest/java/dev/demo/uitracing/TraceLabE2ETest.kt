package dev.demo.uitracing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceLabE2ETest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun exercisesComposeScreensAndStateChanges() {
        compose.onNodeWithText("Compose Trace Lab").assertIsDisplayed()
        compose.onNodeWithText("Recomposition playground").assertIsDisplayed()
        compose.onNodeWithContentDescription("Counter value 0").assertIsDisplayed()
        repeat(10) { compose.onNodeWithText("Recompose").performClick() }
        compose.onNodeWithContentDescription("Counter value 10").assertIsDisplayed()
        compose.onNodeWithContentDescription("Animated value").assertIsDisplayed()

        compose.onNodeWithTag("detail-switch").performClick()
        compose.onNodeWithText("AnimatedVisibility content").assertIsDisplayed()
        compose.onNodeWithText("Open dialog").performClick()
        compose.onNodeWithText("Hierarchy dialog").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onAllNodesWithText("Hierarchy dialog").assertCountEquals(0)
        compose.onNodeWithTag("overview-scroll").performScrollToNode(hasTestTag("android-view"))
        compose.onNodeWithTag("android-view").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Android TextView · tapped 1 times").assertIsDisplayed()

        compose.onNodeWithText("Gallery").performClick()
        compose.onNodeWithText("Compose component gallery").assertIsDisplayed()
        compose.onNodeWithTag("search-field").performTextInput("Canvas")
        compose.onAllNodesWithText("Canvas").assertCountEquals(2)
        compose.onAllNodesWithText("Buttons").assertCountEquals(0)

        compose.onNodeWithText("Feed").performClick()
        compose.onNodeWithText("Scrolling content").assertIsDisplayed()
        compose.onNodeWithTag("feed-list").performScrollToIndex(100)
        compose.onNodeWithText("Composable row 100 · tap to recompose").assertIsDisplayed().performClick()
        compose.onNodeWithText("Grid").performClick()
        compose.onNodeWithText("Two-column lazy grid").assertIsDisplayed()
        compose.onNodeWithTag("tile-grid").performScrollToIndex(60)
        compose.onNodeWithText("Tile 60").assertIsDisplayed().performClick()
        compose.onNodeWithText("Forms").performClick()
        compose.onNodeWithText("Form controls").assertIsDisplayed()
        compose.onNodeWithContentDescription("Radio selection").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Enabled").assertIsDisplayed()
        compose.onNodeWithTag("name-field").performTextInput("Trace test")
        compose.onNodeWithText("Preview: Trace test").assertIsDisplayed()
        compose.onNodeWithText("Motion").performClick()
        compose.onNodeWithText("Motion and graphics").assertIsDisplayed()
        compose.onNodeWithText("Toggle animated card").performClick()
        compose.onAllNodesWithText("Animated content").assertCountEquals(0)
        compose.onNodeWithText("Flow").performClick()
        compose.onNodeWithText("Coroutines and Flow").assertIsDisplayed()
        compose.onNodeWithText("Update state").performClick()
        compose.onNodeWithText("StateFlow count: 1").assertIsDisplayed()
        compose.onNodeWithText("Emit transient event").performClick()
        compose.onNodeWithText("SharedFlow event: Button tapped at", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Start cold flow").performClick()
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("Cold flow stream: 5 / 5").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }
}
