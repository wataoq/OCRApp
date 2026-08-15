package com.local.bookocr.data.repository

import com.local.bookocr.data.local.dao.OcrResultDao
import com.local.bookocr.data.local.entity.OcrResultEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeOcrResultDao : OcrResultDao {
    private var nextId = 1L
    val results = MutableStateFlow<List<OcrResultEntity>>(emptyList())

    override suspend fun upsert(result: OcrResultEntity): Long {
        val id = nextId++
        results.update { list -> list.filterNot { it.pageId == result.pageId } + result.copy(id = id) }
        return id
    }

    override fun observeByPageId(pageId: Long): Flow<OcrResultEntity?> =
        results.map { list -> list.find { it.pageId == pageId } }

    override suspend fun getByPageIdOnce(pageId: Long): OcrResultEntity? =
        results.value.find { it.pageId == pageId }

    override suspend fun updateEditedText(pageId: Long, editedText: String, updatedAt: Long) {
        results.update { list ->
            list.map { if (it.pageId == pageId) it.copy(editedText = editedText, ocrTimestamp = updatedAt) else it }
        }
    }

    override suspend fun resetEditedToRaw(pageId: Long, updatedAt: Long) {
        results.update { list ->
            list.map { if (it.pageId == pageId) it.copy(editedText = it.rawText, ocrTimestamp = updatedAt) else it }
        }
    }
}
