package com.mediavault.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mediavault.app.R
import com.mediavault.app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf("") }
    var tempEndpoint by remember { mutableStateOf("") }

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
            // SECCIÓN: ESCALADO IA (MODO DE PRUEBA / BETA)
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
                                            "Escalado IA con GPU",
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
                                        "Aumenta resolución (hasta 8K) y 60 FPS en la nube",
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
                            "Nota: Esta función es experimental y se procesa 100% en servidores externos con GPU para no ralentizar tu teléfono. No interfiere con las descargas estándar.",
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

            // SECCIÓN: DESCARGAS Y RED
            item {
                SettingsSectionTitle("Descargas y Conectividad")
            }

            item {
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Wifi,
                        title = "Descargar solo con Wi-Fi",
                        subtitle = "Evita el uso de datos móviles en descargas",
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
                        title = "Calidad de Video por Defecto",
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        icon = Icons.Outlined.MusicNote,
                        title = "Formato de Audio por Defecto",
                        subtitle = "${settings.defaultAudioFormat.uppercase()} • ${settings.defaultAudioBitrate}",
                        onClick = {
                            val nextF = when (settings.defaultAudioFormat) {
                                "mp3" -> "m4a"
                                "m4a" -> "opus"
                                "opus" -> "flac"
                                else -> "mp3"
                            }
                            viewModel.updateDefaultAudioFormat(nextF)
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsClickableItem(
                        icon = Icons.Outlined.Cookie,
                        title = "Limpiar Cookies de Sesión",
                        subtitle = "Borrar sesiones guardadas de Instagram, YouTube, etc.",
                        onClick = {
                            viewModel.clearAllCookies()
                            Toast.makeText(context, "Cookies eliminadas", Toast.LENGTH_SHORT).show()
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
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Palette,
                        title = "Color Dinámico (Material You)",
                        subtitle = "Sincronizar paleta con el fondo de pantalla del sistema",
                        checked = settings.isDynamicColor,
                        onCheckedChange = { viewModel.updateIsDynamicColor(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.DataSaverOn,
                        title = "Modo Ahorro de Datos",
                        subtitle = "Reduce el tamaño de miniaturas y vistas previas",
                        checked = settings.dataSaverMode,
                        onCheckedChange = { viewModel.updateDataSaverMode(it) }
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

            // Espacio final
            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
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
                    label = { Text("Pega tu API Key aquí") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateUpscaleApiKey(tempApiKey)
                    showApiKeyDialog = false
                    Toast.makeText(context, "API Key guardada", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo de Endpoint
    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            title = { Text("Endpoint de Servidor GPU") },
            text = {
                OutlinedTextField(
                    value = tempEndpoint,
                    onValueChange = { tempEndpoint = it },
                    label = { Text("URL Base (ej. http://192.168.1.100:8000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateUpscaleEndpoint(tempEndpoint)
                    showEndpointDialog = false
                    Toast.makeText(context, "Endpoint guardado", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
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
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
