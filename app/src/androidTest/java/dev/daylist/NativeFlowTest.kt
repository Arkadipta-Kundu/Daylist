package dev.daylist

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class NativeFlowTest {
    @get:Rule val ui = createAndroidComposeRule<Main>()
    private fun shot(label:String) {
        ui.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        val image=inst.uiAutomation.takeScreenshot()
        val dir=File(inst.targetContext.getExternalFilesDir(null),"visual").apply{mkdirs()}
        val variant=InstrumentationRegistry.getArguments().getString("variant","phone")
        File(dir,"$variant-$label.png").outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
        image.recycle()
    }
    @Test fun captureEditCompleteAndNavigate(){
        shot("today")
        ui.onNodeWithText("Inbox").performClick()
        shot("inbox")
        ui.onNodeWithContentDescription("New task").performClick()
        val title="Review the SQL query plan for the reporting service and record the indexing trade-offs"
        ui.onNodeWithContentDescription("New task text").performTextInput("$title tomorrow at 7 pm !! [] Inspect indexes")
        ui.waitUntil(10000){ui.onAllNodesWithText("High").fetchSemanticsNodes().isNotEmpty()}
        shot("composer")
        ui.onNodeWithContentDescription("Add details").performClick()
        ui.onNodeWithText("Description",useUnmergedTree=true).performScrollTo().performClick()
        shot("metadata")
        ui.onNodeWithText("Apply").performClick()
        ui.onNodeWithContentDescription("Send task").performClick()
        ui.onNodeWithText(title).assertIsDisplayed()
        shot("upcoming")
        ui.onNodeWithText(title).performClick()
        shot("details")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Complete "+title).performClick()
        ui.onNodeWithContentDescription("More options").performClick()
        ui.onNodeWithText("Completed").performClick()
        ui.onNodeWithText(title).assertIsDisplayed()
        shot("completed")
        ui.onNodeWithContentDescription("More options").performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Color theme").assertIsDisplayed()
        shot("settings")
    }
}
