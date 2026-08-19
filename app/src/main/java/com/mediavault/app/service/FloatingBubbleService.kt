package com.mediavault.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.mediavault.app.MainActivity
import com.mediavault.downloader.detector.PlatformDetector
import com.mediavault.downloader.extractor.UniversalMediaExtractor
import com.mediavault.downloader.model.Platform
import com.mediavault.downloader.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FloatingBubbleService : Service() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var platformDetector: PlatformDetector

    @Inject
    lateinit var universalMediaExtractor: UniversalMediaExtractor

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var windowManager: WindowManager? = null

    private var bubbleView: View? = null
    private var popupView: View? = null
    private var dismissZoneView: View? = null

    private var bubbleParams: WindowManager.LayoutParams? = null
    private var currentMediaUrl: String? = null
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    private val CHANNEL_ID = "mediavault_floating_bubble"
    private val NOTIFICATION_ID = 4001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Modo Burbuja Activo", "Detectando enlaces multimedia"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val detectedUrl = intent?.getStringExtra(EXTRA_MEDIA_URL)
        if (!detectedUrl.isNullOrBlank()) {
            currentMediaUrl = detectedUrl
            showOrUpdateBubble(detectedUrl)
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showOrUpdateBubble(url: String) {
        if (bubbleView != null) {
            resetAutoDismissTimer()
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 300
        }

        // Crear la vista de la burbuja por código
        val bubble = createBubbleLayout()
        bubbleView = bubble

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams!!.x
                    initialY = bubbleParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    showDismissZone()
                    resetAutoDismissTimer()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isClick = false
                    }
                    bubbleParams!!.x = initialX + deltaX
                    bubbleParams!!.y = initialY + deltaY
                    windowManager?.updateViewLayout(bubble, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideDismissZone()
                    if (isClick) {
                        onBubbleClicked()
                    } else {
                        // Si se soltó cerca del fondo, descartar
                        val displayMetrics = resources.displayMetrics
                        if (event.rawY > displayMetrics.heightPixels * 0.8f) {
                            stopSelf()
                        }
                    }
                    resetAutoDismissTimer()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubble, bubbleParams)
            resetAutoDismissTimer()
        } catch (e: Exception) {
            Timber.tag("MediaVaultBubble").e(e, "Error al mostrar overlay de burbuja")
        }
    }

    private fun createBubbleLayout(): View {
        val container = android.widget.FrameLayout(this).apply {
            val size = (56 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(size, size)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#1E88E5"))
                setStroke((2 * resources.displayMetrics.density).toInt(), android.graphics.Color.WHITE)
            }
            elevation = 16f
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.stat_sys_download)
            setColorFilter(android.graphics.Color.WHITE)
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        container.addView(icon)
        return container
    }

    private fun showDismissZone() {
        if (dismissZoneView != null) return
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (90 * resources.displayMetrics.density).toInt(),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        val zone = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            val label = TextView(this@FloatingBubbleService).apply {
                text = "✕ Soltar aquí para descartar"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
            }
            addView(label, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ))
        }

        dismissZoneView = zone
        try {
            windowManager?.addView(zone, params)
        } catch (e: Exception) {
            Timber.tag("MediaVaultBubble").w("Error al agregar zona de descarte: ${e.message}")
        }
    }

    private fun hideDismissZone() {
        dismissZoneView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
            dismissZoneView = null
        }
    }

    private fun onBubbleClicked() {
        val url = currentMediaUrl ?: return
        showQuickDownloadPopup(url)
    }

    private fun showQuickDownloadPopup(url: String) {
        if (popupView != null) {
            dismissPopup()
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            (320 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val popup = createPopupLayout(url)
        popupView = popup

        try {
            windowManager?.addView(popup, params)
        } catch (e: Exception) {
            Timber.tag("MediaVaultBubble").e(e, "Error al mostrar popup de descarga")
        }
    }

    private fun createPopupLayout(url: String): View {
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 24 * resources.displayMetrics.density
                setColor(android.graphics.Color.parseColor("#1C1B1F"))
                setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#44474F"))
            }
            elevation = 24f
        }

        val platform = platformDetector.detect(url)

        val titleView = TextView(this).apply {
            text = "Enlace Detectado (${platform.name})"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitleView = TextView(this).apply {
            text = url.take(65) + if (url.length > 65) "..." else ""
            setTextColor(android.graphics.Color.parseColor("#CAC4D0"))
            textSize = 12f
            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, (16 * resources.displayMetrics.density).toInt())
        }

        val statusView = TextView(this).apply {
            text = "Listo para descargar"
            setTextColor(android.graphics.Color.parseColor("#80D8FF"))
            textSize = 13f
            setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
        }

        val btnDownload = Button(this).apply {
            text = "Descargar Directo"
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundColor(android.graphics.Color.parseColor("#64B5F6"))
            setOnClickListener {
                statusView.text = "Encolando descarga..."
                serviceScope.launch {
                    try {
                        downloadRepository.enqueueDownload(
                            url = url,
                            title = "Descarga Rápida (${platform.name})",
                            platform = platform,
                            formatId = "best",
                            quality = "1080p"
                        )
                        withContext(Dispatchers.Main) {
                            statusView.text = "¡Descarga iniciada!"
                            Handler(Looper.getMainLooper()).postDelayed({
                                dismissPopup()
                                stopSelf()
                            }, 1200)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            statusView.text = "Error: ${e.message}"
                        }
                    }
                }
            }
        }

        val btnOpenApp = Button(this).apply {
            text = "Abrir en MediaVault"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#333842"))
            setOnClickListener {
                val appIntent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(appIntent)
                dismissPopup()
                stopSelf()
            }
        }

        val btnClose = Button(this).apply {
            text = "Cerrar"
            setTextColor(android.graphics.Color.parseColor("#938F99"))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener {
                dismissPopup()
            }
        }

        card.addView(titleView)
        card.addView(subtitleView)
        card.addView(statusView)
        card.addView(btnDownload)
        card.addView(btnOpenApp)
        card.addView(btnClose)

        return card
    }

    private fun dismissPopup() {
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
            popupView = null
        }
    }

    private fun resetAutoDismissTimer() {
        autoDismissRunnable?.let { autoDismissHandler.removeCallbacks(it) }
        autoDismissRunnable = Runnable {
            dismissPopup()
            stopSelf()
        }
        autoDismissHandler.postDelayed(autoDismissRunnable!!, 10000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Burbuja Flotante MediaVault",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de overlay y descarga rápida al copiar enlaces"
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
        autoDismissRunnable?.let { autoDismissHandler.removeCallbacks(it) }
        dismissPopup()
        hideDismissZone()
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
            bubbleView = null
        }
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_MEDIA_URL = "extra_media_url"

        fun startWithUrl(context: Context, url: String) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                putExtra(EXTRA_MEDIA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
