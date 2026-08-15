package com.local.bookocr.imageprocessor

import com.local.bookocr.imageprocessor.model.DewarpMesh
import com.local.bookocr.imageprocessor.model.MeshPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dewarp mesh is persisted so the dewarp can be regenerated; verify its JSON round-trip. */
class DewarpMeshTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `mesh survives a JSON round-trip`() {
        val original = DewarpMesh(
            topPoints = listOf(MeshPoint(0f, 0.12f), MeshPoint(0.5f, 0.05f), MeshPoint(1f, 0.12f)),
            bottomPoints = listOf(MeshPoint(0f, 0.9f), MeshPoint(0.5f, 0.97f), MeshPoint(1f, 0.9f)),
        )
        val restored = json.decodeFromString<DewarpMesh>(json.encodeToString(original))
        assertEquals(original, restored)
    }

    @Test
    fun `default mesh has matching edge counts and is valid`() {
        val mesh = DewarpMesh.default(3)
        assertEquals(3, mesh.topPoints.size)
        assertEquals(3, mesh.bottomPoints.size)
        assertTrue(mesh.isValid)
    }

    @Test
    fun `mesh with mismatched edge counts is invalid`() {
        val mesh = DewarpMesh(
            topPoints = listOf(MeshPoint(0f, 0f), MeshPoint(1f, 0f)),
            bottomPoints = listOf(MeshPoint(0f, 1f)),
        )
        assertFalse(mesh.isValid)
    }

    @Test
    fun `mesh with a single point per edge is invalid`() {
        val mesh = DewarpMesh(listOf(MeshPoint(0f, 0f)), listOf(MeshPoint(0f, 1f)))
        assertFalse(mesh.isValid)
    }
}
