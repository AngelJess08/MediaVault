package com.mediavault.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mediavault.app.MainActivity
import com.mediavault.app.R

class QuickDownloadWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET_PROGRESS) {
            val status = intent.getStringExtra(EXTRA_STATUS) ?: "Descargando..."
            val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
            updateAllWidgets(context, status, progress)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, "Listo para descargar", 0)
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET_PROGRESS = "com.mediavault.app.ACTION_UPDATE_WIDGET_PROGRESS"
        const val EXTRA_STATUS = "EXTRA_STATUS"
        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"

        fun updateAllWidgets(context: Context, status: String, progress: Int) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuickDownloadWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId, status, progress)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            status: String,
            progress: Int
        ) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("ACTION_QUICK_PASTE", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_quick_download).apply {
                setTextViewText(R.id.widget_status, status)
                setProgressBar(R.id.widget_progress, 100, progress, progress == 0 && status.contains("Descargando"))
                setOnClickPendingIntent(R.id.btn_widget_paste, pendingIntent)
                setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
