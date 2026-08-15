package com.local.bookocr.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.bookocr.data.local.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test - requires a device/emulator and does NOT run under `./gradlew test`. Verifies
 * that [AppDatabase.MIGRATION_1_2] is additive: a page inserted under schema v1 keeps its original
 * image and gains the new nullable/default image-processing columns, so upgrading never loses data.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesExistingPageAndAddsProcessingColumns() {
        val dbName = "migration-test.db"

        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, author, createdAt, updatedAt) " +
                    "VALUES (1, 'テスト', NULL, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO pages (id, bookId, pageNumber, storedImagePath, createdAt, updatedAt) " +
                    "VALUES (1, 1, 1, 'original.jpg', 0, 0)",
            )
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

        migratedDb.query(
            "SELECT storedImagePath, processedImagePath, processingSettingsJson, processingVersion " +
                "FROM pages WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("original.jpg", cursor.getString(0))
            assertTrue(cursor.isNull(1)) // processedImagePath default NULL
            assertTrue(cursor.isNull(2)) // processingSettingsJson default NULL
            assertEquals(0, cursor.getInt(3)) // processingVersion default 0
        }
        migratedDb.close()
    }

    @Test
    fun migrate2To3_preservesPageAndAddsDewarpAndVariantColumns() {
        val dbName = "migration-test-2-3.db"

        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, author, createdAt, updatedAt) " +
                    "VALUES (1, 'テスト', NULL, 0, 0)",
            )
            // A v2 row already carrying a perspective processed image.
            db.execSQL(
                "INSERT INTO pages " +
                    "(id, bookId, pageNumber, storedImagePath, processedImagePath, " +
                    "processingSettingsJson, processingVersion, createdAt, updatedAt) " +
                    "VALUES (1, 1, 1, 'original.jpg', 'persp.jpg', '{}', 1, 0, 0)",
            )
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)

        migratedDb.query(
            "SELECT storedImagePath, processedImagePath, dewarpImagePath, dewarpMeshJson, activeVariant " +
                "FROM pages WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("original.jpg", cursor.getString(0))
            assertEquals("persp.jpg", cursor.getString(1)) // existing perspective image preserved
            assertTrue(cursor.isNull(2)) // dewarpImagePath default NULL
            assertTrue(cursor.isNull(3)) // dewarpMeshJson default NULL
            assertEquals("PERSPECTIVE", cursor.getString(4)) // activeVariant default preserves behavior
        }
        migratedDb.close()
    }
}
