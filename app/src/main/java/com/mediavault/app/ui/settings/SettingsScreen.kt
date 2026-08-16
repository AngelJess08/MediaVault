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

            // SECCIÓN: COOKIES Y ACCESO A REDES SOCIALES
            item {
                SettingsSectionTitle("Sesiones y Cookies (Twitter/X, Instagram, etc.)")
            }

            item {
                SettingsCard {
                    Text(
                        "Inicia sesión directamente para descargar contenido restringido o privado sin exportar archivos manualmente:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://x.com/login" to "Twitter / X" },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://m.facebook.com/login/" to "Facebook" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Facebook", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { webViewLoginTarget = "https://accounts.google.com/" to "YouTube" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("YouTube", style = MaterialTheme.typography.labelSmall)
                        }
                    }

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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${cookie.platform} (${cookie.domain})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteCookie(cookie.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
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
