package com.kompact.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import com.kompact.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * Test per la casella "gradi esatti" del Rotate editor: digitando un valore
 * (es. +112, -79) la rotazione si applica in tempo reale; slider e pulsanti
 * aggiornano il valore mostrato nella casella.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RotateEditorDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val confirmed = AtomicReference<Float?>(null)

    private fun setContent(currentRotation: Float = 0f) {
        confirmed.set(null)
        composeRule.setContent {
            RotateEditorDialog(
                currentRotation = currentRotation,
                previewUri = null,
                colorFilter = null,
                onConfirm = { confirmed.set(it) },
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun rotationFieldText(): String =
        composeRule.onNodeWithTag("rotation_input").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    private fun setRotationField(text: String) {
        composeRule.onNodeWithTag("rotation_input")
            .performSemanticsAction(SemanticsActions.SetText) { setText ->
                setText(AnnotatedString(text))
            }
        composeRule.waitForIdle()
    }

    private fun assertDegreesLabel(degrees: Int) {
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.degrees_format, degrees))
            .assertExists()
    }

    private fun applyAndGetConfirmed(): Float {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.apply)).performClick()
        composeRule.waitForIdle()
        return confirmed.get() ?: Float.NaN
    }

    @Test(timeout = 30_000)
    fun typingExactDegrees_appliesRealtimeAndConfirms() {
        setContent(0f)
        setRotationField("+112")

        // La rotazione si applica in tempo reale: etichetta e casella aggiornate.
        assertDegreesLabel(112)
        assertEquals("+112", rotationFieldText())

        assertEquals(112f, applyAndGetConfirmed(), 0.001f)
    }

    @Test(timeout = 30_000)
    fun typingNegativeDegrees_appliesAndConfirms() {
        setContent(0f)
        setRotationField("-79")

        assertDegreesLabel(-79)
        assertEquals("-79", rotationFieldText())

        assertEquals(-79f, applyAndGetConfirmed(), 0.001f)
    }

    @Test(timeout = 30_000)
    fun rotateRightButton_updatesTextField() {
        setContent(0f)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.rotate_right)).performClick()
        composeRule.waitForIdle()

        // Il valore nella casella rispecchia la rotazione dei pulsanti.
        assertEquals("+90", rotationFieldText())
        assertDegreesLabel(90)
    }

    @Test(timeout = 30_000)
    fun slider_updatesTextField() {
        setContent(0f)
        composeRule.onNodeWithTag("rotation_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(90f)
            }
        composeRule.waitForIdle()

        // Il valore nella casella rispecchia la rotazione dello slider.
        assertEquals("+90", rotationFieldText())
        assertDegreesLabel(90)
    }

    @Test(timeout = 30_000)
    fun resetButton_clearsTextField() {
        setContent(0f)
        setRotationField("+45")
        assertEquals("+45", rotationFieldText())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.reset)).performClick()
        composeRule.waitForIdle()

        assertEquals("0", rotationFieldText())
        assertDegreesLabel(0)
    }
}
