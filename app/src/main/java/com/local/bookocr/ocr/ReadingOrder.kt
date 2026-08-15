package com.local.bookocr.ocr

/** Geometry-only representation that keeps reading-order logic unit-testable without ML Kit. */
internal data class ReadingOrderBlock(
    val originalIndex: Int,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?,
) {
    val hasBounds: Boolean get() = left != null && top != null && right != null && bottom != null
    val width: Int get() = if (hasBounds) right!! - left!! else 0
    val height: Int get() = if (hasBounds) bottom!! - top!! else 0
    val centerX: Int get() = if (hasBounds) left!! + width / 2 else 0
}

/**
 * Uses right-to-left column order only when most detected blocks are visibly vertical. Horizontal
 * pages retain the conventional top-to-bottom, left-to-right block order.
 */
internal fun readingOrder(blocks: List<ReadingOrderBlock>): List<Int> {
    val bounded = blocks.filter { it.hasBounds && it.width > 0 && it.height > 0 }
    val vertical = bounded.isNotEmpty() &&
        bounded.count { it.height >= it.width * VERTICAL_ASPECT_RATIO } >=
        kotlin.math.ceil(bounded.size * VERTICAL_MAJORITY).toInt()

    val ordered = if (vertical) {
        blocks.sortedWith(
            compareBy<ReadingOrderBlock> { !it.hasBounds }
                .thenByDescending { it.centerX }
                .thenBy { it.top ?: Int.MAX_VALUE }
                .thenBy { it.originalIndex },
        )
    } else {
        blocks.sortedWith(
            compareBy<ReadingOrderBlock> { !it.hasBounds }
                .thenBy { it.top ?: Int.MAX_VALUE }
                .thenBy { it.left ?: Int.MAX_VALUE }
                .thenBy { it.originalIndex },
        )
    }
    return ordered.map { it.originalIndex }
}

private const val VERTICAL_ASPECT_RATIO = 1.5
private const val VERTICAL_MAJORITY = 0.6
