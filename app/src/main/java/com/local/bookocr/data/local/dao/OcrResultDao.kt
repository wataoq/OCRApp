package com.local.bookocr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.bookocr.data.local.entity.OcrResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: OcrResultEntity): Long

    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId")
    fun observeByPageId(pageId: Long): Flow<OcrResultEntity?>

    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId")
    suspend fun getByPageIdOnce(pageId: Long): OcrResultEntity?

    @Query("UPDATE ocr_results SET editedText = :editedText, ocrTimestamp = :updatedAt WHERE pageId = :pageId")
    suspend fun updateEditedText(pageId: Long, editedText: String, updatedAt: Long)

    @Query("UPDATE ocr_results SET editedText = rawText, ocrTimestamp = :updatedAt WHERE pageId = :pageId")
    suspend fun resetEditedToRaw(pageId: Long, updatedAt: Long)
}
