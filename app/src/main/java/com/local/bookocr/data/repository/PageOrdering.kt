package com.local.bookocr.data.repository

import com.local.bookocr.data.local.entity.PageEntity

/**
 * Pages with a known [PageEntity.pageNumber] sort first, in numeric order. Pages without one
 * (e.g. freshly imported, not yet numbered) fall back to import order (createdAt, then id as a
 * final deterministic tiebreaker) and are listed after all numbered pages.
 */
fun List<PageEntity>.sortedForDisplay(): List<PageEntity> = sortedWith(
    compareBy(
        { it.pageNumber == null },
        { it.pageNumber ?: Int.MAX_VALUE },
        { it.createdAt },
        { it.id },
    ),
)
