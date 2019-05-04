package com.pinnacleimagingsystems.ambientviewer2.tasks

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class CopyTask(val context: Context) {
    companion object {
        private const val TAG = "CopyTask"

        private var serial_ = 0
        fun nextSerial() = serial_.also {
            serial_++
        }
    }

    sealed class CopyResult {
        data class UnsupportedType(val mimeType: String): CopyResult()
        data class Success(val mimeType: String, val file: File): CopyResult()
        data class Failure(val exception: Exception): CopyResult()
    }

    @WorkerThread
    fun copyFile(uri: Uri, fileName: String?): CopyResult {
        val contentResolver = context.contentResolver

        val mimeType = contentResolver.getType(uri)
        val stream = contentResolver.openInputStream(uri)

        if (mimeType != "image/jpeg") {
            return CopyResult.UnsupportedType(mimeType.orEmpty())
        }

        if (stream == null) {
            return CopyResult.Failure(IOException("Error opening stream to $uri"))
        }

        try {
            stream.use {
                val file = copyStream(stream, fileName)
                return CopyResult.Success(mimeType, file)
            }
        } catch (e: Exception) {
            return CopyResult.Failure(e)
        }
    }

    @WorkerThread
    private fun copyStream(stream: InputStream, fileName: String?): File {
        val cacheDir = context.cacheDir
        val outputFile = File(cacheDir, (fileName ?: "tempFile") + "_" + nextSerial())

        val buffer = ByteArray(1024 * 1024)

        FileOutputStream(outputFile).use { out ->
            while (stream.available() > 0) {
                val bytes = stream.read(buffer, 0, buffer.size)
                out.write(buffer, 0, bytes)
            }
        }

        return outputFile
    }

}