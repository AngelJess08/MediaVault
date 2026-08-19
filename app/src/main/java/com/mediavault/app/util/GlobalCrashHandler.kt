package com.mediavault.app.util

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GlobalCrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashLog(thread, throwable)
        } catch (e: Exception) {
            Timber.e(e, "Error al guardar el archivo de crash log")
        } finally {
            // Relanzar el crash al manejador por defecto del sistema
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        val crashDir = File(context.filesDir, "crash_logs")
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }

        val sdfDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val timestampStr = sdfDate.format(Date())
        val crashFile = File(crashDir, "crash_${timestampStr}.txt")

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }

        val report = buildString {
            append("====================================================\n")
            append("           MEDIAVAULT CRASH REPORT                  \n")
            append("====================================================\n")
            append("Fecha y Hora : ${Date()}\n")
            append("Paquete      : ${context.packageName}\n")
            append("Versión App  : ${pInfo?.versionName ?: "N/A"} (${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo?.longVersionCode else pInfo?.versionCode})\n")
            append("Dispositivo  : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n")
            append("Android SDK  : ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})\n")
            append("Hilo         : ${thread.name} (ID: ${thread.id})\n")
            append("Tipo Error   : ${throwable.javaClass.name}\n")
            append("Mensaje      : ${throwable.message ?: "Sin mensaje"}\n")
            append("====================================================\n")
            append("STACK TRACE:\n\n")
            append(stackTrace)
            append("\n====================================================\n")
        }

        crashFile.writeText(report)
        Timber.tag("MediaVaultCrash").e("Crash log persistido en: ${crashFile.absolutePath}")
    }

    companion object {
        fun install(context: Context) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is GlobalCrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(context.applicationContext, currentHandler))
                Timber.tag("MediaVaultCrash").d("GlobalCrashHandler instalado correctamente.")
            }
        }

        fun getCrashLogs(context: Context): List<File> {
            val crashDir = File(context.filesDir, "crash_logs")
            if (!crashDir.exists()) return emptyList()
            return crashDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        }

        fun clearCrashLogs(context: Context) {
            val crashDir = File(context.filesDir, "crash_logs")
            if (crashDir.exists()) {
                crashDir.deleteRecursively()
            }
        }
    }
}
