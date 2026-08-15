package com.local.bookocr.imageprocessor.model

import kotlinx.serialization.Serializable

/**
 * User-guided curved-page dewarp specification. Unlike the planar homography used by
 * [PerspectiveQuad] (which can only correct a flat trapezoid), this models the *nonlinear*
 * vertical curvature a book page bows into near the spine/gutter.
 *
 * The correction is a ruled surface: the user marks the text block's top edge as a curve
 * ([topPoints]) and its bottom edge as a curve ([bottomPoints]), both left-to-right in normalized
 * image coordinates [0,1]. The dewarper remaps that curved region onto an axis-aligned rectangle,
 * straightening the bowed lines. Point counts on the two edges must match (paired columns).
 *
 * Purely declarative data (no OpenCV types) so it can be persisted and the dewarped image
 * regenerated from the original at any time - image correction stays non-destructive.
 */
@Serializable
data class DewarpMesh(
    val topPoints: List<MeshPoint>,
    val bottomPoints: List<MeshPoint>,
) {
    /** Valid when both edges have the same count and at least 2 points (a straightenable curve). */
    val isValid: Boolean
        get() = topPoints.size == bottomPoints.size && topPoints.size >= MIN_POINTS_PER_EDGE

    companion object {
        const val MIN_POINTS_PER_EDGE = 2

        /**
         * Default mesh for [pointsPerEdge] columns spanning the full image: a straight top edge and
         * straight bottom edge the user then drags to follow the page's real curvature.
         */
        fun default(pointsPerEdge: Int = 3): DewarpMesh {
            val step = if (pointsPerEdge <= 1) 0f else 1f / (pointsPerEdge - 1)
            val top = (0 until pointsPerEdge).map { MeshPoint(it * step, TOP_EDGE_Y) }
            val bottom = (0 until pointsPerEdge).map { MeshPoint(it * step, BOTTOM_EDGE_Y) }
            return DewarpMesh(top, bottom)
        }

        private const val TOP_EDGE_Y = 0.1f
        private const val BOTTOM_EDGE_Y = 0.9f
    }
}

/** A single control point in normalized image coordinates, each component in [0.0, 1.0]. */
@Serializable
data class MeshPoint(val x: Float, val y: Float)
