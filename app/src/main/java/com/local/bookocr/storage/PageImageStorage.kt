package com.local.bookocr.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns the app-private copies of page photos. Kept as an interface (rather than a concrete
 * class used directly by repositories) so tests can substitute an in-memory fake without an
 * Android [Context] - see the fake in `src/test`.
 */
interface ImageStorage {
    suspend fun importImage(sourceUri: Uri): Result<String>
    fun resolveFile(storedImagePath: String): File
    fun exists(storedImagePath: String): Boolean
    suspend fun delete(storedImagePath: String): Boolean

    /**
     * Processed (corrected) images live in a separate directory from originals so the two
     * lifecycles never collide - deleting a processed image can never touch the original.
     */
    fun allocateProcessedFile(): File
    fun resolveProcessedFile(processedPath: String): File
    fun processedExists(processedPath: String): Boolean
    suspend fun deleteProcessed(processedPath: String): Boolean

    /** Dewarp variant images, kept in their own directory (sibling of processed images). */
    fun allocateDewarpFile(): File
    fun resolveDewarpFile(dewarpPath: String): File
    fun dewarpExists(dewarpPath: String): Boolean
    suspend fun deleteDewarp(dewarpPath: String): Boolean
}

/**
 * Owns the app-private copies of page photos under `filesDir/page_images/`. The UI and
 * repositories never touch the filesystem or the original Photo Picker [Uri] directly -
 * once [importImage] returns, only the returned stored path (a UUID-based filename) is
 * persisted, and the original content Uri is not relied upon again.
 */
class PageImageStorage(private val context: Context) : ImageStorage {

    private val imagesDir: File by lazy {
        File(context.filesDir, IMAGES_DIR_NAME).apply { mkdirs() }
    }

    private val processedDir: File by lazy {
        File(context.filesDir, PROCESSED_DIR_NAME).apply { mkdirs() }
    }

    private val dewarpDir: File by lazy {
        File(context.filesDir, DEWARP_DIR_NAME).apply { mkdirs() }
    }

    override suspend fun importImage(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val fileName = "${UUID.randomUUID()}.${resolveExtension(sourceUri)}"
        val destination = File(imagesDir, fileName)
        try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(StorageException("画像を開けませんでした"))
            input.use { stream ->
                destination.outputStream().use { output -> stream.copyTo(output) }
            }
            if (destination.length() == 0L) {
                destination.delete()
                return@withContext Result.failure(StorageException("画像の読み込みに失敗しました"))
            }
            Result.success(fileName)
        } catch (e: IOException) {
            destination.delete()
            Result.failure(StorageException("画像の保存に失敗しました", e))
        } catch (e: SecurityException) {
            destination.delete()
            Result.failure(StorageException("画像へのアクセスが拒否されました", e))
        }
    }

    override fun resolveFile(storedImagePath: String): File = File(imagesDir, storedImagePath)

    override fun exists(storedImagePath: String): Boolean = resolveFile(storedImagePath).exists()

    override suspend fun delete(storedImagePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveFile(storedImagePath)
        if (file.exists()) file.delete() else true
    }

    override fun allocateProcessedFile(): File = File(processedDir, "${UUID.randomUUID()}.jpg")

    override fun resolveProcessedFile(processedPath: String): File = File(processedDir, processedPath)

    override fun processedExists(processedPath: String): Boolean = resolveProcessedFile(processedPath).exists()

    override suspend fun deleteProcessed(processedPath: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveProcessedFile(processedPath)
        if (file.exists()) file.delete() else true
    }

    override fun allocateDewarpFile(): File = File(dewarpDir, "${UUID.randomUUID()}.jpg")

    override fun resolveDewarpFile(dewarpPath: String): File = File(dewarpDir, dewarpPath)

    override fun dewarpExists(dewarpPath: String): Boolean = resolveDewarpFile(dewarpPath).exists()

    override suspend fun deleteDewarp(dewarpPath: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveDewarpFile(dewarpPath)
        if (file.exists()) file.delete() else true
    }

    private fun resolveExtension(uri: Uri): String =
        when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

    companion object {
        private const val IMAGES_DIR_NAME = "page_images"
        private const val PROCESSED_DIR_NAME = "processed_images"
        private const val DEWARP_DIR_NAME = "dewarp_images"
    }
}
