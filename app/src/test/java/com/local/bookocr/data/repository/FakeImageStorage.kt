package com.local.bookocr.data.repository

import android.net.Uri
import com.local.bookocr.storage.ImageStorage
import java.io.File

class FakeImageStorage : ImageStorage {
    val deletedPaths = mutableListOf<String>()
    var nextImportResult: Result<String> = Result.success("stub-page.jpg")

    override suspend fun importImage(sourceUri: Uri): Result<String> = nextImportResult

    override fun resolveFile(storedImagePath: String): File = File(storedImagePath)

    override fun exists(storedImagePath: String): Boolean = true

    override suspend fun delete(storedImagePath: String): Boolean {
        deletedPaths += storedImagePath
        return true
    }
}
