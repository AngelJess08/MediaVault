package com.mediavault.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mediavault.app.service.ClipboardMonitorService
import com.mediavault.app.service.FloatingBubbleService
import com.mediavault.app.ui.cookies.CookieLoginDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val cookiesList by viewModel.cookiesList.collectAsState()
    val context = LocalContext.current

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var showImportCookiesDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf("") }
    var tempEndpoint by remember { mutableStateOf("") }
    var tempCookiesText by remember { mutableStateOf("") }

    var webViewLoginTarget: Pair<String, String>? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes y Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECCIÓN: ESCALADO IA (MODO DE PRUEBA / BETA - AISLADO)
            item {
                SettingsSectionTitle("Inteligencia Artificial y Escalado")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.upscaleBetaEnabled)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (settings.upscaleBetaEnabled)
                        CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                    else null
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Escalado con IA (Beta)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "BETA",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        "Subir resolución y FPS en servidor GPU remoto",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settings.upscaleBetaEnabled,
                                onCheckedChange = { viewModel.updateUpscaleBetaEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Aviso: El escalado IA puede fallar, consumir datos extra y tardar varios minutos según el servidor GPU en la nube. Si falla, el archivo nativo ya descargado se conserva 100% intacto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        AnimatedVisibility(visible = settings.upscaleBetaEnabled) {
                            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Proveedor de Escalado GPU", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val providers = listOf("replicate" to "Replicate", "fal_ai" to "Fal.ai", "custom" to "Servidor Propio")
                                    providers.forEach { (key, label) ->
                                        FilterChip(
                                            selected = settings.upscaleProvider == key,
                                            onClick = { viewModel.updateUpscaleProvider(key) },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                if (settings.upscaleProvider == "custom") {
                                    SettingsClickableItem(
                                        icon = Icons.Outlined.Dns,
                                        title = "Endpoint URL del Servidor",
                                        subtitle = settings.upscaleEndpoint.ifEmpty { "http://10.0.2.2:8000" },
                                        onClick = {
                                            tempEndpoint = settings.upscaleEndpoint
                                            showEndpointDialog = true
                                        }
                                    )
                                } else {
                                    SettingsClickableItem(
                                        icon = Icons.Outlined.Key,
                                        title = "Clave de API (${if (settings.upscaleProvider == "replicate") "Replicate" else "Fal.ai"})",
                                        subtitle = if (settings.upscaleApiKey.isNotEmpty()) "••••••••••••••••" else "No configurada (Toca para ingresar)",
                                        onClick = {
                                            tempApiKey = settings.upscaleApiKey
                                            showApiKeyDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Oculto temporalmente mientras se estabiliza el flujo de descarga principal — reactivar en BottomNavBar.kt y SettingsScreen.kt
            /*
            // SECCIÓN: MODO NAVEGADOR (OPCIONAL / SECUNDARIO)
            item {
                SettingsSectionTitle("Navegación Web Integrada (Opcional)")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isBrowserModeEnabled)
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (settings.isBrowserModeEnabled)
                        CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)))
                    else null
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Habilitar Modo Navegador",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "Navegador in-app con detector de videos y bloqueo de anuncios",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settings.isBrowserModeEnabled,
                                onCheckedChange = { viewModel.updateIsBrowserModeEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Aviso: La función principal de MediaVault sigue siendo pegar URLs directamente. El Modo Navegador es una opción secundaria aislada con filtro anti-malware, bloqueo de popups y captura pasiva de streams mientras exploras sitios web.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            */

            // SECCIÓN: MODO BURBUJA FLOTANTE (DESCARGA RÁPIDA)
            item {
                SettingsSectionTitle("Modo Burbuja Flotante y Portapapeles")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isFloatingBubbleEnabled)
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (settings.isFloatingBubbleEnabled)
                        CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)))
                    else null
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Burbuja de Descarga Rápida",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "Muestra una burbuja flotante al copiar un enlace de video/audio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settings.isFloatingBubbleEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        if (android.provider.Settings.canDrawOverlays(context)) {
                                            viewModel.updateIsFloatingBubbleEnabled(true)
                                            ClipboardMonitorService.start(context)
                                            Toast.makeText(context, "Modo Burbuja activado", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showOverlayPermissionDialog = true
                                        }
                                    } else {
                                        viewModel.updateIsFloatingBubbleEnabled(false)
                                        ClipboardMonitorService.stop(context)
                                        FloatingBubbleService.stop(context)
                                        Toast.makeText(context, "Modo Burbuja desactivado", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Privacidad Garantizada: El monitor solo inspecciona enlaces que coincidan con plataformas de video/audio. Jamás guarda, registra ni transmite ningún otro texto de tu portapapeles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // SECCIÓN: COOKIES Y ACCESO A REDES SOCIALES
            item {
                SettingsSectionTitle("Sesiones y Cookies de Plataformas")
            }

            item {
                SettingsCard {
                    Text(
                        "Inicia sesión directamente para descargar contenido restringido o de cuentas seguidas en redes sociales:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Fila 1: Twitter / X e Instagram
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://twitter.com/i/flow/login" to "Twitter / X" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Twitter / X", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://www.instagram.com/accounts/login/" to "Instagram" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Instagram", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Fila 2: Facebook y TikTok
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://www.facebook.com/login" to "Facebook" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Facebook", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://www.tiktok.com/login/phone-or-email/email" to "TikTok" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("TikTok", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Fila 3: Reddit y Vimeo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://www.reddit.com/login/" to "Reddit" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reddit", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://vimeo.com/log_in" to "Vimeo" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Vimeo", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Fila 4: Twitch y Pinterest
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://www.twitch.tv/login" to "Twitch" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Twitch", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://www.pinterest.com/login/" to "Pinterest" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Pinterest", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Nota sobre YouTube: YouTube opera con contenido público sin requerir inicio de sesión. Google bloquea estrictamente los inicios de sesión dentro de vistas web integradas por su política de protección anti-phishing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    SettingsClickableItem(
                        icon = Icons.Outlined.FileUpload,
                        title = "Importar archivo cookies.txt (Netscape)",
                        subtitle = "Pega o importa cookies exportadas desde extensiones",
                        onClick = { showImportCookiesDialog = true }
                    )

                    if (cookiesList.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Text(
                            "Cookies Guardadas Activas (${cookiesList.size}):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        cookiesList.forEach { cookie ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${cookie.platform} (${cookie.domain})",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.testCookieSession(cookie.platform) { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Probar Sesión", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteCookie(cookie.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = { viewModel.clearAllCookies() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Eliminar Todas", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // SECCIÓN: DESCARGAS Y RED
            item {
                SettingsSectionTitle("Descargas y Conectividad")
            }

            item {
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Wifi,
                        title = "Descargar solo con Wi-Fi",
                        subtitle = "Pausa descargas si el dispositivo pasa a datos móviles",
                        checked = settings.wifiOnlyDownload,
                        onCheckedChange = { viewModel.updateWifiOnly(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        icon = Icons.Outlined.Speed,
                        title = "Límite de Velocidad de Descarga",
                        subtitle = if (settings.downloadSpeedLimit > 0) "${settings.downloadSpeedLimit} KB/s" else "Sin límite (Máxima velocidad)",
                        onClick = {
                            val nextLimit = when (settings.downloadSpeedLimit) {
                                0 -> 1000
                                1000 -> 3000
                                3000 -> 5000
                                else -> 0
                            }
                            viewModel.updateSpeedLimit(nextLimit)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        icon = Icons.Outlined.HighQuality,
                        title = "Calidad Nativa por Defecto",
                        subtitle = settings.defaultVideoQuality,
                        onClick = {
                            val nextQ = when (settings.defaultVideoQuality) {
                                "1080p" -> "4K (2160p)"
                                "4K (2160p)" -> "720p"
                                else -> "1080p"
                            }
                            viewModel.updateDefaultVideoQuality(nextQ)
                        }
                    )
                }
            }

            // SECCIÓN: ALMACENAMIENTO Y PRIVACIDAD
            item {
                SettingsSectionTitle("Almacenamiento y Seguridad")
            }

            item {
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Bloqueo Biométrico / Huella",
                        subtitle = "Protege el acceso a la app y a la Bóveda Privada",
                        checked = settings.isBiometricEnabled,
                        onCheckedChange = { viewModel.updateIsBiometricEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "Papelera Temporal",
                        subtitle = "Conservar archivos eliminados antes del borrado final",
                        checked = settings.trashEnabled,
                        onCheckedChange = { viewModel.updateTrashEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        icon = Icons.Outlined.DeleteForever,
                        title = "Vaciar Papelera",
                        subtitle = "Eliminar permanentemente los archivos en papelera",
                        onClick = {
                            viewModel.emptyTrash()
                            Toast.makeText(context, "Papelera vaciada con éxito", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // SECCIÓN: RESPALDOS Y PERSONALIZACIÓN
            item {
                SettingsSectionTitle("Respaldos y Apariencia")
            }

            item {
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "Tema Oscuro",
                        subtitle = "Forzar interfaz en tema oscuro",
                        checked = settings.isDarkTheme,
                        onCheckedChange = { viewModel.updateIsDarkTheme(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        icon = Icons.Outlined.CloudDownload,
                        title = "Exportar Historial (Copia JSON)",
                        subtitle = "Guardar respaldo local de descargas y etiquetas",
                        onClick = {
                            viewModel.exportHistory { path ->
                                Toast.makeText(context, "Respaldo exportado en: $path", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }

            // SECCIÓN: DIAGNÓSTICO Y SOPORTE TÉCNICO
            item {
                SettingsSectionTitle("Diagnóstico y Soporte Técnico")
            }

            item {
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Registros de Error y Crashes",
                        subtitle = "Ver y copiar archivos de diagnóstico de caídas de la app",
                        onClick = {
                            showDiagnosticDialog = true
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Diálogo de Login WebView
    webViewLoginTarget?.let { (url, platform) ->
        CookieLoginDialog(
            initialUrl = url,
            platformName = platform,
            onDismiss = { webViewLoginTarget = null },
            onCookiesCaptured = { currentUrl, cookies ->
                val domain = try { java.net.URI(currentUrl).host ?: platform.lowercase() } catch (e: Exception) { platform.lowercase() }
                viewModel.saveCookies(platform, domain, cookies)
                Toast.makeText(context, "Cookies de $platform guardadas correctamente", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Diálogo para Importar cookies.txt
    if (showImportCookiesDialog) {
        AlertDialog(
            onDismissRequest = { showImportCookiesDialog = false },
            title = { Text("Importar cookies.txt (Netscape)") },
            text = {
                Column {
                    Text(
                        "Pega el contenido de tu archivo cookies.txt exportado:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempCookiesText,
                        onValueChange = { tempCookiesText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("# Netscape HTTP Cookie File\n.twitter.com\tTRUE\t/...") },
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempCookiesText.isNotBlank()) {
                            viewModel.importCookiesText(tempCookiesText) { count ->
                                Toast.makeText(context, "Se importaron $count dominios con cookies", Toast.LENGTH_SHORT).show()
                            }
                            tempCookiesText = ""
                            showImportCookiesDialog = false
                        }
                    }
                ) {
                    Text("Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportCookiesDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de API Key
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Configurar Clave de API") },
            text = {
                OutlinedTextField(
                    value = tempApiKey,
                    onValueChange = { tempApiKey = it },
                    label = { Text("API Token / Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUpscaleApiKey(tempApiKey)
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Endpoint Propio
    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            title = { Text("Endpoint de Servidor GPU Propio") },
            text = {
                OutlinedTextField(
                    value = tempEndpoint,
                    onValueChange = { tempEndpoint = it },
                    label = { Text("http://ip-servidor:8000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUpscaleEndpoint(tempEndpoint)
                        showEndpointDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Justificación y Solicitud de Permiso de Superposición (Burbuja Flotante)
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Permiso de Superposición Requerido") },
            text = {
                Text(
                    "Para mostrar la burbuja de descarga rápida sobre otras aplicaciones cuando copias un enlace, MediaVault necesita el permiso 'Mostrar sobre otras apps'.\n\n" +
                            "• Privacidad: La app únicamente evalúa si el texto copiado es un enlace multimedia.\n" +
                            "• Jamás se registra, almacena ni comparte tu portapapeles personal.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDialog = false
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(fallbackIntent)
                        }
                    }
                ) {
                    Text("Abrir Ajustes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Diagnóstico y Crash Logs
    if (showDiagnosticDialog) {
        val crashLogs = remember { com.mediavault.app.util.GlobalCrashHandler.getCrashLogs(context) }
        var selectedCrashContent by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                selectedCrashContent = null
                showDiagnosticDialog = false
            },
            icon = { Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (selectedCrashContent == null) "Diagnóstico de Errores" else "Detalle del Crash") },
            text = {
                if (selectedCrashContent != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCrashContent!!,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (crashLogs.isEmpty()) {
                    Text(
                        "No se han registrado caídas ni errores no capturados. La aplicación está operando con normalidad.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(crashLogs) { file ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCrashContent = file.readText()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("${file.length()} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedCrashContent != null) {
                    Row {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crash Log", selectedCrashContent))
                            Toast.makeText(context, "Log copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Copiar")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, selectedCrashContent)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Compartir reporte de error"))
                        }) {
                            Text("Compartir")
                        }
                    }
                } else {
                    Button(onClick = { showDiagnosticDialog = false }) {
                        Text("Cerrar")
                    }
                }
            },
            dismissButton = {
                if (selectedCrashContent != null) {
                    TextButton(onClick = { selectedCrashContent = null }) {
                        Text("Volver a Lista")
                    }
                } else if (crashLogs.isNotEmpty()) {
                    TextButton(onClick = {
                        com.mediavault.app.util.GlobalCrashHandler.clearCrashLogs(context)
                        Toast.makeText(context, "Logs eliminados", Toast.LENGTH_SHORT).show()
                        showDiagnosticDialog = false
                    }) {
                        Text("Borrar Todo")
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
