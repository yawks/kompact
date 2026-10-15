package com.kompact.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.kompact.R
import com.kompact.model.TextOverlayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * Regressione per il bug del Text editor: dopo aver "applicato" una casella di
 * testo (tap fuori dalla casella), un tap sulla casella esistente deve
 * riselezionarla e riaprire la tastiera (edit mode, campo editabile).
 *
 * Prima del fix il tap veniva intercettato dal BasicTextField readOnly (che ha
 * il proprio tap-to-focus): il campo andava a fuoco ma restava readOnly, quindi
 * la casella non entrava in edit mode e non si poteva digitare. A questo si
 * aggiungeva il ritardo di ~300ms introdotto dal rilevamento del doppio tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextEditorDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val savedTexts = AtomicReference<List<TextOverlayConfig>?>()

    private fun setContent(texts: List<TextOverlayConfig>) {
        // Clock manuale: il rilevamento del doppio tap ritarderebbe onTap di
        // ~300ms (doubleTapTimeout). Con autoAdvance=false il test osserva lo
        // stato subito dopo il tap, prima che quel timeout possa mascherare
        // il comportamento.
        composeRule.mainClock.autoAdvance = false
        savedTexts.set(null)
        composeRule.setContent {
            TextEditorDialog(
                imageUri = null,
                texts = texts,
                onTextsChange = { savedTexts.set(it) },
                onDismiss = {},
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun tapOnCanvasOutsideBox() {
        // Tappa sul layer canvas, a sinistra del quadrato centrato (85% del lato
        // minore): non colpisce nessuna casella di testo.
        composeRule.onNodeWithTag("text_canvas").performTouchInput {
            down(Offset(center.x * 0.1f, center.y))
            up()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun tapNode(text: String) {
        composeRule.onNodeWithText(text).performTouchInput {
            down(center)
            up()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun advanceFrame() {
        // Con autoAdvance=false la ricomposizione va applicata avanzando un frame.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    /** Testo del campo editabile attualmente in edit mode (null se non editabile). */
    private fun editableFieldText(): String? =
        composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .firstOrNull()?.config?.get(SemanticsProperties.EditableText)?.text

    /**
     * Digita [input] nel campo che mostra [text] e restituisce il nuovo testo.
     * Se il campo non e' editabile (regressione: resta readOnly dopo il tap)
     * performTextInput fallisce -> il test fallisce.
     */
    private fun typeInto(text: String, input: String): String {
        composeRule.onNodeWithText(text).performTextInput(input)
        advanceFrame()
        return editableFieldText() ?: error("nessun campo editabile dopo la digitazione")
    }

    @Test(timeout = 30_000)
    fun tapAppliedTextBox_entersEditModeAndAllowsTyping() {
        // Casella gia' creata e "applicata": selezionata ma non in edit
        // (stato in cui si trova dopo un tap fuori dalla casella).
        setContent(listOf(TextOverlayConfig(text = "Ciao", offsetX = 0.5f, offsetY = 0.5f)))
        composeRule.onNodeWithText("Colors").assertIsDisplayed()

        // Tap sulla casella esistente: deve entrare in edit mode.
        tapNode("Ciao")

        // Digita nel campo: deve essere editabile e recepire il testo.
        val typed = typeInto("Ciao", "!")
        assertTrue("digitare deve modificare il testo (era 'Ciao', ora '$typed')", typed != "Ciao")

        // Casella ancora selezionata (pannello controlli visibile).
        composeRule.onNodeWithText("Colors").assertIsDisplayed()
    }

    @Test(timeout = 30_000)
    fun tapAnotherTextBox_switchesSelectionAndAllowsTyping() {
        setContent(
            listOf(
                TextOverlayConfig(text = "AAA", offsetX = 0.3f, offsetY = 0.5f),
                TextOverlayConfig(text = "BBB", offsetX = 0.7f, offsetY = 0.5f),
            )
        )

        // Tappa la seconda casella: deve selezionarla e renderla editabile.
        tapNode("BBB")
        val typed = typeInto("BBB", "!")

        assertTrue("la casella tappata deve diventare editabile (era 'BBB', ora '$typed')", typed != "BBB")
        // La prima casella resta intatta.
        composeRule.onNodeWithText("AAA").assertExists()
    }

    @Test(timeout = 30_000)
    fun editTypeThenTapOutside_keepsAppliedText() {
        setContent(listOf(TextOverlayConfig(text = "Ciao", offsetX = 0.5f, offsetY = 0.5f)))

        // Entra in edit, digita, poi tap fuori per "applicare".
        tapNode("Ciao")
        val typed = typeInto("Ciao", "!")
        assertTrue("digitare deve modificare il testo", typed != "Ciao")
        tapOnCanvasOutsideBox()

        // Il testo digitato resta applicato sulla casella.
        composeRule.onNodeWithText(typed).assertExists()
    }

    @Test(timeout = 30_000)
    fun tapOutsideTextBox_deselects() {
        setContent(listOf(TextOverlayConfig(text = "Ciao", offsetX = 0.5f, offsetY = 0.5f)))
        composeRule.onNodeWithText("Colors").assertIsDisplayed()

        tapOnCanvasOutsideBox()

        // Fuori dalla casella: deseleziona e mostra il placeholder.
        composeRule.onNodeWithText("Colors").assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.tap_text_to_edit))
            .assertIsDisplayed()
    }
}
