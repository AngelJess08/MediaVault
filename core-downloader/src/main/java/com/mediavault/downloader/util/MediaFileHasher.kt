package com.mediavault.downloader.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object MediaFileHasher {

    /**
     * Calcula el hash SHA-256 de un archivo para detectar archivos duplicados por contenido.
     */
    suspend fun computeFileSha256(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ""
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)

        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        val hashBytes = digest.digest()
        hashBytes.joinToString("") { "%02x".format(it) }
    }
}
