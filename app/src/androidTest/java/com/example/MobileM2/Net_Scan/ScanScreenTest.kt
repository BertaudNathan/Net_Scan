package com.example.MobileM2.Net_Scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun showsExampleListAndOpensCreationDialog() {
        compose.onNodeWithText(text(R.string.demo_data)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.building_name)).assertIsDisplayed()
    }

    @Test
    fun leavingEditedFormRequiresConfirmation() {
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.building_name)).performTextInput("Magasin")
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.onNodeWithText(text(R.string.discard_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.keep_editing)).performClick()
        compose.onNodeWithText(text(R.string.dialog_title)).assertIsDisplayed()
    }

    @Test
    fun creationClearlyStatesThatNothingIsSaved() {
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.create_scan)).performClick()
        compose.onNodeWithText(text(R.string.prototype_notice)).assertIsDisplayed()
    }

    @Test
    fun backFromListRequiresExitConfirmation() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.exit_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stay)).performClick()
        compose.onNodeWithText(text(R.string.new_scan)).assertIsDisplayed()
    }
}
