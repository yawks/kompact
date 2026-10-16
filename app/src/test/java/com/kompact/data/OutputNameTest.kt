package com.kompact.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputNameTest {

    // --- buildRequestedOutputName ---

    @Test
    fun requestedNameKeepsOriginalNameWithoutEditSuffix() {
        val result = buildRequestedOutputName("photo.jpg", "jpg", customName = null)
        assertEquals("photo.jpg", result)
    }

    @Test
    fun requestedNameUsesEffectiveExtensionWhenFormatChanges() {
        val result = buildRequestedOutputName("photo.jpg", "webp", customName = null)
        assertEquals("photo.webp", result)
    }

    @Test
    fun requestedNameUsesCustomNameAndAppendsExtension() {
        val result = buildRequestedOutputName("photo.jpg", "jpg", customName = "vacanza")
        assertEquals("vacanza.jpg", result)
    }

    @Test
    fun requestedNameDoesNotDuplicateExtensionForCustomName() {
        val result = buildRequestedOutputName("photo.jpg", "jpg", customName = "vacanza.jpg")
        assertEquals("vacanza.jpg", result)
    }

    @Test
    fun requestedNameFallsBackToOriginalNameWhenCustomNameIsBlank() {
        val result = buildRequestedOutputName("photo.jpg", "jpg", customName = "   ")
        assertEquals("photo.jpg", result)
    }

    @Test
    fun requestedNameHandlesSourceWithoutExtension() {
        val result = buildRequestedOutputName("photo", "png", customName = null)
        assertEquals("photo.png", result)
    }

    @Test
    fun requestedNameFallsBackToFileWhenSourceNameMissing() {
        val result = buildRequestedOutputName(null, "jpg", customName = null)
        assertEquals("file.jpg", result)
    }

    // --- resolveOutputName ---

    @Test
    fun resolveKeepsOriginalNameWhenNoConflict() {
        val result = resolveOutputName("photo.jpg", isCustomName = false) { false }
        assertEquals("photo.jpg", result)
    }

    @Test
    fun resolveAddsEditSuffixBeforeExtensionOnConflict() {
        val result = resolveOutputName("photo.jpg", isCustomName = false) { it == "photo.jpg" }
        assertEquals("photo-edit.jpg", result)
    }

    @Test
    fun resolveKeepsCustomNameEvenOnConflict() {
        val result = resolveOutputName("vacanza.jpg", isCustomName = true) { true }
        assertEquals("vacanza.jpg", result)
    }

    @Test
    fun resolveAddsEditSuffixToNameWithoutExtension() {
        val result = resolveOutputName("photo", isCustomName = false) { true }
        assertEquals("photo-edit", result)
    }

    @Test
    fun resolveOnlyChecksTheRequestedName() {
        val checkedNames = mutableListOf<String>()
        val result = resolveOutputName("photo.webp", isCustomName = false) {
            checkedNames += it
            false
        }
        assertEquals("photo.webp", result)
        assertEquals(listOf("photo.webp"), checkedNames)
    }
}
