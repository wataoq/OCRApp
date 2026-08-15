package com.local.bookocr.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.local.bookocr.data.local.dao.BookDao
import com.local.bookocr.data.local.dao.OcrResultDao
import com.local.bookocr.data.local.dao.PageDao
import com.local.bookocr.data.local.entity.BookEntity
import com.local.bookocr.data.local.entity.OcrResultEntity
import com.local.bookocr.data.local.entity.PageEntity

@Database(
    entities = [BookEntity::class, PageEntity::class, OcrResultEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun pageDao(): PageDao
    abstract fun ocrResultDao(): OcrResultDao

    companion object {
        const val DATABASE_NAME = "bookocr.db"
    }
}
