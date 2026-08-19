package com.mediavault.downloader.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

data class SecurityAuditEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val target: String,
    val details: String
)

@Singleton
class SecurityAuditLogger @Inject constructor() {

    private val events = CopyOnWriteArrayList<SecurityAuditEvent>()
    private val _eventsCount = MutableStateFlow(0)
    val eventsCount: StateFlow<Int> = _eventsCount.asStateFlow()

    fun logEvent(eventType: String, target: String, details: String) {
        val event = SecurityAuditEvent(
            eventType = eventType,
            target = target,
            details = details
        )
        events.add(event)
        _eventsCount.value = events.size
    }

    fun getAuditEvents(): List<SecurityAuditEvent> = events.toList()

    fun exportLogAsText(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("=== REPORTE DE AUDITORÍA DE SEGURIDAD LOCAL - MEDIAVAULT ===\n")
        sb.append("Total de eventos registrados: ${events.size}\n\n")

        for (e in events) {
            sb.append("[${sdf.format(Date(e.timestamp))}] [${e.eventType}]\n")
            sb.append("   Destino/URL: ${e.target}\n")
            sb.append("   Detalles: ${e.details}\n")
            sb.append("------------------------------------------------------------\n")
        }
        return sb.toString()
    }

    fun clearLog() {
        events.clear()
        _eventsCount.value = 0
    }
}
