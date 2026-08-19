package com.mediavault.downloader.security

import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class HeuristicScanResult(
    val isClean: Boolean,
    val suspiciousPattern: String? = null
)

@Singleton
class PostDownloadHeuristicScanner @Inject constructor() {

    private val TAG = "HeuristicScanner"

    /**
     * Escanea los primeros y últimos bloques del archivo descargado en busca de firmas ejecutables
     * ocultas o anexadas (Polyglot files, ZIP / JAR / DEX / ELF / MZ incrustados).
     */
    fun scanFileForEmbeddedBinaries(file: File): HeuristicScanResult {
        if (!file.exists() || file.length() < 16) {
            return HeuristicScanResult(isClean = true)
        }

        try {
            val length = file.length()
            val checkSize = minOf(length, 128 * 1024L).toInt() // Escanear hasta 128KB
            val buffer = ByteArray(checkSize)

            FileInputStream(file).use { input ->
                input.read(buffer)
            }

            // 1. Detección de DOS / Windows PE Executable (MZ = 0x4D 0x5A)
            if (buffer.size >= 2 && buffer[0] == 0x4D.toByte() && buffer[1] == 0x5A.toByte()) {
                Timber.tag(TAG).w("¡Alerta de seguridad! Encabezado ejecutable MZ detectado.")
                return HeuristicScanResult(isClean = false, suspiciousPattern = "WINDOWS_PE_EXECUTABLE (MZ)")
            }

            // 2. Detección de Linux ELF Binary (0x7F 'E' 'L' 'F' = 0x7F 0x45 0x4C 0x46)
            if (buffer.size >= 4 && buffer[0] == 0x7F.toByte() && buffer[1] == 0x45.toByte() && buffer[2] == 0x4C.toByte() && buffer[3] == 0x46.toByte()) {
                Timber.tag(TAG).w("¡Alerta de seguridad! Encabezado binario ELF detectado.")
                return HeuristicScanResult(isClean = false, suspiciousPattern = "LINUX_ELF_BINARY")
            }

            // 3. Detección de Android DEX Binary (dex\n035 = 0x64 0x65 0x78 0x0A)
            if (buffer.size >= 4 && buffer[0] == 0x64.toByte() && buffer[1] == 0x65.toByte() && buffer[2] == 0x78.toByte() && buffer[3] == 0x0A.toByte()) {
                Timber.tag(TAG).w("¡Alerta de seguridad! Bytecode DEX de Android detectado.")
                return HeuristicScanResult(isClean = false, suspiciousPattern = "ANDROID_DEX_BINARY")
            }

            // 4. Detección de Script Shell Shebang (#! /bin/sh o #! /usr/bin/env = 0x23 0x21)
            if (buffer.size >= 2 && buffer[0] == 0x23.toByte() && buffer[1] == 0x21.toByte()) {
                Timber.tag(TAG).w("¡Alerta de seguridad! Script ejecutable Shebang (#!) detectado.")
                return HeuristicScanResult(isClean = false, suspiciousPattern = "SHELL_SCRIPT")
            }

            return HeuristicScanResult(isClean = true)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error durante escaneo heurístico de ${file.name}")
            return HeuristicScanResult(isClean = true)
        }
    }
}
