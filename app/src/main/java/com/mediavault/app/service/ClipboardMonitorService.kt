package com.mediavault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.mediavault.app.MainActivity
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.model.Platform
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardMonitorService : Service() {

    @Inject
    lateinit var platformDetector: PlatformDetector

    private var clipboardManager: ClipboardManager? = null
    private var lastHandledUrl: String? = null

    private val CHANNEL_ID = "mediavault_clipboard_monitor"
    private val NOTIFICATION_ID = 4002

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildForegroundNotification(
                "Detector de Enlaces Activo",
                "Monitoreando enlaces multimedia en segundo plano"
            )
        )
        Timber.tag("MediaVaultClipboard").d("ClipboardMonitorService iniciado")
    }

    private fun handleClipboardChange() {
        try {
            val clip = clipboardManager?.primaryClip ?: return
            if (clip.itemCount == 0) return

            val item = clip.getItemAt(0) ?: return
            val text = item.text?.toString() ?: return

            // Aislamiento estricto de privacidad: NO procesar si no parece URL
            val trimmed = text.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                return
            }

            // Extraer la primera URL del texto si contiene texto adicional
            val urlPattern = Pattern.compile("https?://\\S+")
            val matcher = urlPattern.matcher(trimmed)
            if (!matcher.find()) return

            val extractedUrl = matcher.group(0) ?: return

            if (extractedUrl == lastHandledUrl) {
                return
            }

            val platform = platformDetector.detect(extractedUrl)

            // Solo disparar la burbuja si es una plataforma de video/audio reconocida
            if (platform != Platform.UNKNOWN) {
                lastHandledUrl = extractedUrl
                Timber.tag("MediaVaultClipboard").i("Enlace multimedia detectado en portapapeles (${platform.name})")

                // Si se cuenta con permiso de overlay, mostrar la burbuja flotante
                if (Settings.canDrawOverlays(this)) {
                    FloatingBubbleService.startWithUrl(this, extractedUrl)
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaVaultClipboard").w("Error en listener de portapapeles: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor de Portapapeles MediaVault",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitorea enlaces de video/audio copiados para descarga rápida"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        Timber.tag("MediaVaultClipboard").d("ClipboardMonitorService detenido")
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardMonitorService::class.java))
        }
    }
}
