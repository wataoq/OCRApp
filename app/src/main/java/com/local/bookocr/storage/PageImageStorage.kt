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

    private fun resolveExtension(uri: Uri): String =
        when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

    companion object {
        private const val IMAGES_DIR_NAME = "page_images"
    }
}
