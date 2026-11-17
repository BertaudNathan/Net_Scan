package com.example.MobileM2.Net_Scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MobileM2.Net_Scan.data.RoomScanRepository
import com.example.MobileM2.Net_Scan.data.ScanDatabase
import com.example.MobileM2.Net_Scan.ui.ScanListViewModel
import com.example.MobileM2.Net_Scan.ui.ScanScreen
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScanScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ScanDatabase
    private lateinit var databaseName: String

    private fun text(id: Int) = context.getString(id)

    @Before fun setup() {
        databaseName = "scan-test-${UUID.randomUUID()}.db"
        db = Room.databaseBuilder(context, ScanDatabase::class.java, databaseName).build()
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(databaseName)
    }

    private fun showScreen(
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        onPreferences: () -> Unit = {},
    ) {
        val viewModel = ScanListViewModel(RoomScanRepository(db.scanDao()))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                NetScanTheme { ScanScreen(onExit = {}, onPreferences = onPreferences, viewModel = viewModel) }
            }

        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.empty_title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun settingsButtonIsAccessibleAndOpensPreferences() {
        var opened = false
        showScreen(onPreferences = { opened = true })
        compose.onNodeWithContentDescription(text(R.string.open_preferences))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun primaryActionIsInTheBottomThumbZoneWithLargeTouchTarget() {
        showScreen()
        val button = compose.onNodeWithText(text(R.string.new_scan))
        button.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val bounds = button.fetchSemanticsNode().boundsInWindow
        assertTrue("Action should be in the bottom quarter", bounds.top >= root.top + root.height * 0.75f)
        assertTrue("Action should be reachable from either side", bounds.width >= root.width * 0.8f)
    }

    @Test fun largeTextAndRtlKeepFormActionsReachable() {
        showScreen(fontScale = 2f, direction = LayoutDirection.Rtl)
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cancel))
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText(text(R.string.create_scan))
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val cancel = compose.onNodeWithText(text(R.string.cancel)).fetchSemanticsNode().boundsInWindow
        val create = compose.onNodeWithText(text(R.string.create_scan)).fetchSemanticsNode().boundsInWindow
        assertTrue("Confirmation should be below cancellation", create.top > cancel.bottom)
        compose.onNodeWithText(text(R.string.create_scan)).performClick()
        compose.onNodeWithText(text(R.string.building_required)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.building_name))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.building_required)))
    }

    @Test fun savesAndDisplaysScan() {
        showScreen()
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.building_name)).performTextInput("Magasin")
        compose.onNodeWithText(text(R.string.client_name)).performTextInput("Client")
        compose.onNodeWithText(text(R.string.create_scan)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Magasin").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Magasin").assertIsDisplayed()
        compose.onNodeWithText("Client").assertIsDisplayed()
    }

    @Test fun requiresBuildingName() {
        showScreen()
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.create_scan)).performClick()
        compose.onNodeWithText(text(R.string.building_required)).assertIsDisplayed()
    }

    @Test fun leavingEditedFormRequiresConfirmation() {
        showScreen()
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.building_name)).performTextInput("Magasin")
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.onNodeWithText(text(R.string.discard_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.keep_editing)).performClick()
        compose.onNodeWithText(text(R.string.dialog_title)).assertIsDisplayed()
    }

    @Test fun tappingOutsideEditedFormRequiresConfirmation() {
        showScreen()
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.building_name)).performTextInput("Magasin")
        compose.onNode(isDialog()).performTouchInput {
            click(Offset(width / 2f, height * 0.1f))
        }
        compose.onNodeWithText(text(R.string.discard_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.keep_editing)).performClick()
        compose.onNodeWithText(text(R.string.dialog_title)).assertIsDisplayed()
    }

    @Test fun tappingOutsideEmptyFormClosesIt() {
        showScreen()
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNode(isDialog()).performTouchInput {
            click(Offset(width / 2f, height * 0.1f))
        }
        compose.onNodeWithText(text(R.string.dialog_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.new_scan)).assertIsDisplayed()
    }

    @Test fun touchingInsideFormDoesNotDismissItAndButtonRespondsToTouch() {
        showScreen()
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.dialog_description)).performTouchInput { click() }
        compose.onNodeWithText(text(R.string.dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.create_scan)).performTouchInput { click() }
        compose.onNodeWithText(text(R.string.building_required)).assertIsDisplayed()
    }

    @Test fun databaseSurvivesReopening() = runBlocking {
        val repository = RoomScanRepository(db.scanDao())
        repository.createScan("Entrepôt", "Client", "Adresse")
        db.close()
        db = Room.databaseBuilder(context, ScanDatabase::class.java, databaseName).build()
        val scan = db.scanDao().observeAll().first().single()
        assertEquals("Entrepôt", scan.building)
        assertEquals("Client", scan.client)
        assertEquals("Adresse", scan.address)
    }
}
