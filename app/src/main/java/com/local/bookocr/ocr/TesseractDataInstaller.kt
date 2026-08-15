package com.local.bookocr.ocr

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TesseractLanguageModel(
    val language: String,
    val assetName: String,
    val sha256: String,
)

/** Installs bundled traineddata atomically into the app-private layout expected by Tesseract. */
class TesseractDataInstaller(private val context: Context) {

    suspend fun ensureInstalled(model: TesseractLanguageModel): File = withContext(Dispatchers.IO) {
        val dataRoot = File(context.filesDir, DATA_ROOT)
        val tessdataDir = File(dataRoot, TESSDATA_DIRECTORY)
        check(tessdataDir.isDirectory || tessdataDir.mkdirs()) {
            "OCR言語モデルの保存先を作成できませんでした"
        }
        val target = File(tessdataDir, model.assetName)
        if (target.isFile && target.sha256() == model.sha256) return@withContext dataRoot

        val temporary = File.createTempFile("${model.assetName}.", ".tmp", tessdataDir)
        try {
            context.assets.open("$TESSDATA_DIRECTORY/${model.assetName}").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.sha256() == model.sha256) { "OCR言語モデルの検証に失敗しました" }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            dataRoot
        } finally {
            temporary.delete()
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    private companion object {
        const val DATA_ROOT = "tesseract"
        const val TESSDATA_DIRECTORY = "tessdata"
    }
}
