package com.mediavault.app.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber
import java.security.MessageDigest

object AppIntegrityChecker {

    /**
     * Obtiene el hash SHA-256 de los certificados de firma del APK actual.
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun getApkSignatureSha256(context: Context): List<String> {
        val signaturesList = mutableListOf<String>()
        try {
            val packageManager = context.packageManager
            val packageName = context.packageName

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo

                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }

            signatures?.forEach { sig ->
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(sig.toByteArray())
                val hex = digest.joinToString(":") { String.format("%02X", it) }
                signaturesList.add(hex)
            }
        } catch (e: Exception) {
            Timber.tag("AppIntegrity").e(e, "Error al calcular firma del APK")
        }
        return signaturesList
    }

    /**
     * Valida si el paquete fue modificado o re-empaquetado comparando con firmas esperadas.
     */
    fun isAppGenuine(context: Context, expectedSignatures: Set<String> = emptySet()): Boolean {
        if (expectedSignatures.isEmpty()) return true // Modo desarrollo
        val currentSignatures = getApkSignatureSha256(context)
        val matches = currentSignatures.any { expectedSignatures.contains(it) }
        if (!matches) {
            Timber.tag("AppIntegrity").w("¡Advertencia de integridad! La firma del APK no coincide.")
        }
        return matches
    }
}
