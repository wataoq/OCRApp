package com.local.bookocr.data.repository

import android.net.Uri
import com.local.bookocr.storage.ImageStorage
import java.io.File

class FakeImageStorage : ImageStorage {
    val deletedPaths = mutableListOf<String>()
    val deletedProcessedPaths = mutableListOf<String>()
    val deletedDewarpPaths = mutableListOf<String>()
    val processedPaths = mutableSetOf<String>()
    val dewarpPaths = mutableSetOf<String>()
    var nextImportResult: Result<String> = Result.success("stub-page.jpg")
    private var processedCounter = 0
    private var dewarpCounter = 0

    override suspend fun importImage(sourceUri: Uri): Result<String> = nextImportResult

    override fun resolveFile(storedImagePath: String): File = File(storedImagePath)

    override fun exists(storedImagePath: String): Boolean = true

    override suspend fun delete(storedImagePath: String): Boolean {
        deletedPaths += storedImagePath
        return true
    }

    override fun allocateProcessedFile(): File {
        val name = "processed-${processedCounter++}.jpg"
        processedPaths += name
        return File(System.getProperty("java.io.tmpdir"), name)
    }

    override fun resolveProcessedFile(processedPath: String): File =
        File(System.getProperty("java.io.tmpdir"), processedPath)

    override fun processedExists(processedPath: String): Boolean = processedPaths.contains(processedPath)

    override suspend fun deleteProcessed(processedPath: String): Boolean {
        deletedProcessedPaths += processedPath
        processedPaths -= processedPath
        return true
    }

    override fun allocateDewarpFile(): File {
        val name = "dewarp-${dewarpCounter++}.jpg"
        dewarpPaths += name
        return File(System.getProperty("java.io.tmpdir"), name)
    }

    override fun resolveDewarpFile(dewarpPath: String): File =
        File(System.getProperty("java.io.tmpdir"), dewarpPath)

    override fun dewarpExists(dewarpPath: String): Boolean = dewarpPaths.contains(dewarpPath)

    override suspend fun deleteDewarp(dewarpPath: String): Boolean {
        deletedDewarpPaths += dewarpPath
        dewarpPaths -= dewarpPath
        return true
    }
}
