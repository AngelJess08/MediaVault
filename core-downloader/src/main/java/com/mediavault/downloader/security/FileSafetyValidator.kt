package com.mediavault.downloader.security

import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validador estricto de seguridad de archivos y flujos de red.
 * Verifica Magic Bytes binarios reales, descarta ejecutables/malware y valida tipos MIME
 * antes de almacenar cualquier medio en disco.
 */
@Singleton
class FileSafetyValidator @Inject constructor() {

    private val TAG = "MediaVaultFileSafety"

    // Extensiones prohibidas de software ejecutable e instaladores
    val forbiddenExecutableExtensions = setOf(
        "apk", "exe", "sh", "bat", "msi", "dex", "jar", "cmd",
        "vbs", "ps1", "scr", "pif", "com", "bin", "elf", "so",
        "dll", "dmg", "pkg", "deb", "rpm", "hta", "cpl", "msc"
    )

    // Tipos MIME multimedia legítimos
    private val allowedMimePrefixes = listOf(
        "video/",
        "audio/",
        "image/",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "application/dash+xml",
        "application/octet-stream" // Requiere validación de magic bytes
    )

    /**
     * Verifica si una extensión de archivo es potencialmente peligrosa o ejecutable.
     */
    fun isForbiddenExtension(extension: String?): Boolean {
        if (extension.isNullOrBlank()) return false
        val cleanExt = extension.lowercase().trim().removePrefix(".")
        return forbiddenExecutableExtensions.contains(cleanExt)
    }

    /**
     * Verifica si una URL o nombre de archivo contiene una extensión ejecutable peligrosa.
     */
    fun isForbiddenUrlOrFilename(target: String): Boolean {
        val lower = target.lowercase().trim()
        for (ext in forbiddenExecutableExtensions) {
            if (lower.endsWith(".$ext") || lower.contains(".$ext?") || lower.contains(".$ext#")) {
                Timber.tag(TAG).w("Archivo RECHAZADO por extensión ejecutable peligrosa: $target")
                return true
            }
        }
        return false
    }

    /**
     * Valida si el tipo MIME de la respuesta corresponde a contenido multimedia legítimo.
     */
    fun isAllowedMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true // Si no viene declarado, se validará por magic bytes
        val cleanMime = mimeType.lowercase().trim()

        if (cleanMime.contains("android.package-archive") ||
            cleanMime.contains("x-msdownload") ||
            cleanMime.contains("x-executable") ||
            cleanMime.contains("x-sh") ||
            cleanMime.contains("x-bat") ||
            cleanMime.contains("javascript") ||
            cleanMime.contains("text/html")
        ) {
            Timber.tag(TAG).e("Tipo MIME bloqueado por no ser multimedia: $mimeType")
            return false
        }

        return allowedMimePrefixes.any { cleanMime.startsWith(it) }
    }

    /**
     * Inspecciona los primeros bytes binarios (**Magic Bytes**) del archivo en disco
     * para confirmar que es un formato de video/audio real y no un ejecutable o payload malicioso.
     */
    fun validateMediaMagicBytes(file: File): Boolean {
        if (!file.exists() || file.length() < 12) {
            Timber.tag(TAG).w("Archivo demasiado pequeño o inexistente para validar magic bytes: ${file.name}")
            return false
        }

        return try {
            FileInputStream(file).use { input ->
                validateStreamMagicBytes(input, file.name)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al leer magic bytes de ${file.name}")
            false
        }
    }

    /**
     * Valida los magic bytes desde un InputStream.
     */
    fun validateStreamMagicBytes(input: InputStream, filename: String): Boolean {
        val header = ByteArray(64)
        val readCount = input.read(header)
        if (readCount < 4) return false

        // 1. MP4 / M4A / MOV (ISO Base Media File / ftyp)
        // [offset 4..7: 'ftyp' = 0x66, 0x74, 0x79, 0x70]
        if (readCount >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
            header[6] == 0x79.toByte() && header[7] == 0x70.toByte()
        ) {
            return true
        }

        // 2. Matroska / WebM (EBML header: 0x1A 0x45 0xDF 0xA3)
        if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) {
            return true
        }

        // 3. MPEG Transport Stream (TS: sync byte 0x47 al inicio de paquetes)
        if (header[0] == 0x47.toByte()) {
            return true
        }

        // 4. MP3 con tag ID3 (0x49 0x44 0x33 = 'ID3') o sync frame MPEG (0xFF 0xFB / 0xFF 0xF3 / 0xFF 0xF2)
        if ((header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) ||
            (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
        ) {
            return true
        }

        // 5. AAC ADTS (0xFF 0xF1 / 0xFF 0xF9)
        if (header[0] == 0xFF.toByte() && (header[1] == 0xF1.toByte() || header[1] == 0xF9.toByte())) {
            return true
        }

        // 6. Ogg Container (0x4F 0x67 0x67 0x53 = 'OggS')
        if (header[0] == 0x4F.toByte() && header[1] == 0x67.toByte() &&
            header[2] == 0x67.toByte() && header[3] == 0x53.toByte()
        ) {
            return true
        }

        // 7. FLAC (0x66 0x4C 0x61 0x43 = 'fLaC')
        if (header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() &&
            header[2] == 0x61.toByte() && header[3] == 0x43.toByte()
        ) {
            return true
        }

        // 8. RIFF (WAV / AVI: 0x52 0x49 0x46 0x46 = 'RIFF')
        if (header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte()
        ) {
            return true
        }

        // 9. FLV (0x46 0x4C 0x56 = 'FLV')
        if (header[0] == 0x46.toByte() && header[1] == 0x4C.toByte() && header[2] == 0x56.toByte()) {
            return true
        }

        // 10. Playlists de texto HLS M3U8 (#EXTM3U)
        val headerString = String(header, 0, readCount.coerceAtMost(32))
        if (headerString.startsWith("#EXTM3U") || headerString.startsWith("<?xml")) {
            return true
        }

        Timber.tag(TAG).w("Encabezado binario no reconocido para formato multimedia en: $filename")
        return false
    }
}
