package com.local.bookocr.imageprocessor.model

/** How many physical pages are present in one captured image. */
enum class DocumentLayout {
    SINGLE_PAGE,
    TWO_PAGE_SPREAD,
}

/** Import order for pages extracted from a spread. Japanese books default to right-to-left. */
enum class ReadingDirection {
    RIGHT_TO_LEFT,
    LEFT_TO_RIGHT,
}

enum class SpreadPageSide { RIGHT, LEFT }

fun ReadingDirection.orderedSides(): List<SpreadPageSide> = when (this) {
    ReadingDirection.RIGHT_TO_LEFT -> listOf(SpreadPageSide.RIGHT, SpreadPageSide.LEFT)
    ReadingDirection.LEFT_TO_RIGHT -> listOf(SpreadPageSide.LEFT, SpreadPageSide.RIGHT)
}
