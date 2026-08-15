package com.local.bookocr.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.bookocr.data.local.database.AppDatabase
import com.local.bookocr.data.local.entity.BookEntity
import com.local.bookocr.data.local.entity.OcrResultEntity
import com.local.bookocr.data.local.entity.PageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test - requires a device/emulator and was NOT run in the sandbox that
 * generated this project (no Android SDK / no device available there). Verifies the FK
 * cascade behavior Room enforces at the SQLite level, which an in-memory fake DAO cannot
 * exercise (see the unit tests in src/test for the fakes-based repository coverage).
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseCascadeDeleteTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun deletingBookCascadesToPagesAndOcrResults() = runBlocking {
        val bookId = db.bookDao().insert(BookEntity(title = "テスト", author = null, createdAt = 0, updatedAt = 0))
        val pageId = db.pageDao().insert(
            PageEntity(bookId = bookId, pageNumber = 1, storedImagePath = "a.jpg", createdAt = 0, updatedAt = 0),
        )
        db.ocrResultDao().upsert(
            OcrResultEntity(
                pageId = pageId,
                rawText = "raw",
                editedText = "raw",
                engineId = "fake",
                ocrTimestamp = 0,
                layoutJson = null,
            ),
        )

        db.bookDao().delete(db.bookDao().getByIdOnce(bookId)!!)

        assertTrue(db.pageDao().getForBookOnce(bookId).isEmpty())
        assertNull(db.ocrResultDao().getByPageIdOnce(pageId))
    }
}
