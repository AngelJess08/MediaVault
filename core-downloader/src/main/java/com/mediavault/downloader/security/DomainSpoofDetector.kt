package com.mediavault.downloader.security

import java.net.IDN
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainSpoofDetector @Inject constructor() {

    private val targetBrands = listOf("youtube", "tiktok", "instagram", "twitter", "facebook", "reddit", "vimeo", "google", "apple")

    /**
     * Detecta si un dominio utiliza codificación Punycode / IDN o caracteres Unicode no latinos
     * simulando una marca reconocida (ataque de homóglifos).
     */
    fun isPotentialHomoglyphSpoof(url: String): Boolean {
        return try {
            val uri = URI(if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url)
            val host = uri.host ?: return false

            // Si contiene xn-- (Punycode)
            if (host.startsWith("xn--") || host.contains(".xn--")) {
                val unicodeHost = IDN.toUnicode(host).lowercase()
                return targetBrands.any { brand -> unicodeHost.contains(brand) && !host.contains(brand) }
            }

            // Detección de caracteres no ASCII o caracteres cirílicos comunes que reemplazan letras latinas
            val containsNonAscii = host.any { it.code > 127 }
            if (containsNonAscii) {
                val normalized = host.lowercase()
                return targetBrands.any { brand ->
                    // Similitud visual básica
                    isVisuallySimilar(normalized, brand)
                }
            }

            false
        } catch (e: Exception) {
            false
        }
    }

    private fun isVisuallySimilar(text: String, brand: String): Boolean {
        // Mapeo básico de homóglifos cirílicos comunes: а->a, о->o, е->e, р->p, с->c, у->y, х->x
        val simplified = text
            .replace('а', 'a')
            .replace('о', 'o')
            .replace('е', 'e')
            .replace('р', 'p')
            .replace('с', 'c')
            .replace('у', 'y')
            .replace('х', 'x')

        return simplified.contains(brand) && text != brand
    }
}
