package com.local.bookocr.imageprocessor

import com.local.bookocr.imageprocessor.model.EnhancementMode
import com.local.bookocr.imageprocessor.model.ImageProcessingSettings
import com.local.bookocr.imageprocessor.model.NormalizedRect
import com.local.bookocr.imageprocessor.model.PerspectiveQuad
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The persisted-settings contract is what makes correction re-generatable from the original, so
 * a full JSON round-trip (the exact form stored on [PageEntity.processingSettingsJson]) is verified.
 */
class ImageProcessingSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `full settings survive a JSON round-trip`() {
        val original = ImageProcessingSettings(
            rotationDegrees = 90,
            cropRect = NormalizedRect(0.1f, 0.2f, 0.8f, 0.9f),
            perspectivePoints = PerspectiveQuad(
                0.05f, 0.05f, 0.95f, 0.02f, 0.97f, 0.98f, 0.03f, 0.96f,
            ),
            enhancementMode = EnhancementMode.HIGH_CONTRAST,
        )

        val restored = json.decodeFromString<ImageProcessingSettings>(json.encodeToString(original))

        assertEquals(original, restored)
    }

    @Test
    fun `default settings are identity`() {
        assertTrue(ImageProcessingSettings().isIdentity)
    }

    @Test
    fun `any single correction makes settings non-identity`() {
        assertFalse(ImageProcessingSettings(rotationDegrees = 90).isIdentity)
        assertFalse(ImageProcessingSettings(cropRect = NormalizedRect(0.1f, 0f, 1f, 1f)).isIdentity)
        assertFalse(ImageProcessingSettings(perspectivePoints = PerspectiveQuad()).isIdentity)
        assertFalse(ImageProcessingSettings(enhancementMode = EnhancementMode.GRAYSCALE).isIdentity)
    }

    @Test
    fun `perspective coordinates are converted into crop-local coordinates`() {
        val quad = PerspectiveQuad(
            topLeftX = 0.2f,
            topLeftY = 0.25f,
            topRightX = 0.8f,
            topRightY = 0.25f,
            bottomRightX = 0.8f,
            bottomRightY = 0.75f,
            bottomLeftX = 0.2f,
            bottomLeftY = 0.75f,
        )

        val converted = quad.relativeTo(NormalizedRect(0.2f, 0.25f, 0.8f, 0.75f))

        assertEquals(PerspectiveQuad(), converted)
    }

    @Test
    fun `invalid zero-size crop leaves perspective coordinates unchanged`() {
        val quad = PerspectiveQuad(topLeftX = 0.3f)
        assertEquals(quad, quad.relativeTo(NormalizedRect(0.5f, 0f, 0.5f, 1f)))
    }
}
