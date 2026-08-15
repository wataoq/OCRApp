package com.local.bookocr.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.local.bookocr.data.local.dao.BookDao
import com.local.bookocr.data.local.dao.OcrResultDao
import com.local.bookocr.data.local.dao.PageDao
import com.local.bookocr.data.local.entity.BookEntity
import com.local.bookocr.data.local.entity.OcrResultEntity
import com.local.bookocr.data.local.entity.PageEntity

@Database(
    entities = [BookEntity::class, PageEntity::class, OcrResultEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun pageDao(): PageDao
    abstract fun ocrResultDao(): OcrResultDao

    companion object {
        const val DATABASE_NAME = "bookocr.db"

        /**
         * Adds the non-destructive image-processing columns to `pages`. Additive only - existing
         * rows keep their original image and get null/0 defaults, so no page or photo is ever lost.
         * Destructive migration is deliberately not used (it would delete users' scanned pages).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN processedImagePath TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN processingSettingsJson TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN processingVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the curved-page dewarp variant columns and the active-variant selector. Additive
         * only; existing rows default to the PERSPECTIVE variant, which falls back to the original
         * when no processed image exists - so pre-dewarp pages behave exactly as before.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN dewarpImagePath TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN dewarpMeshJson TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN activeVariant TEXT NOT NULL DEFAULT 'PERSPECTIVE'")
            }
        }
    }
}
