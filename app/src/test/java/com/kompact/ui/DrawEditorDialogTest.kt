package com.kompact.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.kompact.model.DrawConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Regressione per il crash/freeze del Draw editor al tocco del canvas
 * (un dito disegna, due dita zoom/pan della vista).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrawEditorDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var imageFile: File? = null
    private val savedConfig = AtomicReference<DrawConfig?>()

    @Before
    fun setUp() {
        val file = File(composeRule.activity.cacheDir, "draw_test_${System.nanoTime()}.png")
        file.outputStream().use { out ->
            Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        imageFile = file
        savedConfig.set(null)
    }

    private fun setContent(drawConfig: DrawConfig? = null) {
        val uri: Uri? = imageFile?.let { Uri.fromFile(it) }
        composeRule.setContent {
            DrawEditorDialog(
                imageUri = uri,
                drawConfig = drawConfig,
                onDrawConfigChange = { savedConfig.set(it) },
                onDismiss = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun canvas() = composeRule.onNodeWithTag("draw_canvas")

    private fun saveAndGetConfig(): DrawConfig? {
        composeRule.onNodeWithContentDescription("Save").performClick()
        composeRule.waitForIdle()
        return savedConfig.get()
    }

    @Test(timeout = 30_000)
    fun dragOnCanvas_commitsOneStroke() {
        setContent()
        canvas().performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(20f, 20f))
            }
            up()
        }
        composeRule.waitForIdle()
        val saved = saveAndGetConfig()
        assertNotNull("il draw editor deve salvare una config", saved)
        assertEquals("il drag deve produrre un tratto", 1, saved?.strokes?.size)
        assertTrue(
            "il tratto deve contenere piu' di un punto",
            (saved?.strokes?.firstOrNull()?.points?.size ?: 0) > 1,
        )
    }

    @Test(timeout = 30_000)
    fun tapOnCanvas_commitsNothing() {
        setContent()
        canvas().performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()
        val saved = saveAndGetConfig()
        assertNotNull(saved)
        assertEquals("un tap senza movimento non deve produrre tratti", 0, saved?.strokes?.size)
    }

    @Test(timeout = 30_000)
    fun twoSeparateDrags_commitTwoStrokes() {
        setContent()
        canvas().performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(20f, 20f))
            }
            up()
        }
        composeRule.waitForIdle()
        canvas().performTouchInput {
            down(center)
            repeat(5) {
                moveBy(Offset(-20f, 20f))
            }
            up()
        }
        composeRule.waitForIdle()
        val saved = saveAndGetConfig()
        assertEquals("due drag separati devono produrre due tratti", 2, saved?.strokes?.size)
    }

    @Test(timeout = 30_000)
    fun longDragWithManyMoves_commitsStroke() {
        setContent()
        canvas().performTouchInput {
            down(center)
            repeat(200) {
                moveBy(Offset(1f, 1f))
            }
            up()
        }
        composeRule.waitForIdle()
        val saved = saveAndGetConfig()
        assertEquals(1, saved?.strokes?.size)
    }

    @Test(timeout = 30_000)
    fun pinchOnCanvas_doesNotCommitStroke() {
        setContent()
        canvas().performTouchInput {
            var pos0 = center
            var pos1 = center + Offset(50f, 0f)
            down(0, pos0)
            down(1, pos1)
            repeat(5) {
                pos0 -= Offset(10f, 0f)
                pos1 += Offset(10f, 0f)
                moveTo(0, pos0)
                moveTo(1, pos1)
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
        val saved = saveAndGetConfig()
        // Il pinch e' zoom della vista, non disegno: nessun tratto
        assertEquals("il pinch non deve produrre tratti", 0, saved?.strokes?.size)
    }
}

