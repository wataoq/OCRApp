package com.local.bookocr

import android.content.Context
import androidx.room.Room
import com.local.bookocr.data.local.database.AppDatabase
import com.local.bookocr.data.repository.BookRepository
import com.local.bookocr.data.repository.OcrRepository
import com.local.bookocr.data.repository.PageRepository
import com.local.bookocr.imageprocessor.BitmapImageProcessor
import com.local.bookocr.imageprocessor.ImageProcessor
import com.local.bookocr.imageprocessor.OpenCvPageDewarper
import com.local.bookocr.imageprocessor.PageDewarper
import com.local.bookocr.ocr.MlKitOcrEngine
import com.local.bookocr.ocr.OcrEngine
import com.local.bookocr.storage.ImageStorage
import com.local.bookocr.storage.PageImageStorage

/**
 * Small hand-rolled dependency container (no Hilt/Dagger - unnecessary for a single-module
 * app this size). Screens obtain repositories through [BookOcrApplication.container] via
 * their ViewModel factories.
 */
interface AppContainer {
    val bookRepository: BookRepository
    val pageRepository: PageRepository
    val ocrRepository: OcrRepository

    /** Exposed so the image-preprocessing flow can import originals and stage processed files. */
    val imageStorage: ImageStorage
    val imageProcessor: ImageProcessor
    val pageDewarper: PageDewarper
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val applicationContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    override val imageStorage: ImageStorage by lazy { PageImageStorage(applicationContext) }

    override val imageProcessor: ImageProcessor by lazy { BitmapImageProcessor() }

    override val pageDewarper: PageDewarper by lazy { OpenCvPageDewarper() }

    private val ocrEngine: OcrEngine by lazy { MlKitOcrEngine() }

    override val bookRepository: BookRepository by lazy {
        BookRepository(database.bookDao(), database.pageDao(), imageStorage)
    }

    override val pageRepository: PageRepository by lazy {
        PageRepository(database.pageDao(), imageStorage)
    }

    override val ocrRepository: OcrRepository by lazy {
        OcrRepository(database.ocrResultDao(), ocrEngine)
    }
}
