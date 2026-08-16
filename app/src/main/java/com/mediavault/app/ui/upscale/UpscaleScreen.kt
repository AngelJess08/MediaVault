package com.mediavault.app.ui.upscale

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mediavault.app.navigation.Screen
import com.mediavault.storage.db.entity.DownloadEntity
import com.mediavault.storage.db.entity.UpscaleJobEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpscaleScreen(
    navController: NavHostController,
    viewModel: UpscaleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val upscaleJobs by viewModel.upscaleJobs.collectAsState()
    val availableVideos by viewModel.availableVideos.collectAsState()

    var showVideoPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Escalado IA con GPU", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "BETA",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configuración")
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
            if (!settings.upscaleBetaEnabled) {
                // Banner cuando está desactivado
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Módulo de Escalado IA en Prueba",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "El escalado de video por IA se procesa 100% en la nube a través de servidores GPU (Fal.ai, Replicate o tu propio servidor). Esta función es experimental y debe habilitarse en los Ajustes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { navController.navigate(Screen.Settings.route) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Activar en Ajustes")
                            }
                        }
                    }
                }
            } else {
                // Panel de Configuración de Trabajo de Escalado
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Crear Trabajo de Escalado",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Selector de Video
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showVideoPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = uiState.selectedVideo?.title ?: "Toca para seleccionar un video de tu biblioteca",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (uiState.selectedVideo != null) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Selector de Resolución Objetivo
                            Text("Resolución Objetivo (Super-Resolución)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("2x (1080p)", "4x (4K UHD)", "8x (8K Ultra)").forEach { res ->
                                    FilterChip(
                                        selected = uiState.targetResolution == res,
                                        onClick = { viewModel.setTargetResolution(res) },
                                        label = { Text(res) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Selector de FPS (Interpolación)
                            Text("Aumento de Fotogramas (FPS)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30 to "30 FPS", 60 to "60 FPS (Interpolación)").forEach { (fps, label) ->
                                    FilterChip(
                                        selected = uiState.targetFps == fps,
                                        onClick = { viewModel.setTargetFps(fps) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Resumen de tiempo y costo
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Proveedor: ${settings.upscaleProvider.uppercase()}", style = MaterialTheme.typography.labelSmall)
                                    Text("Costo Est.: ~ $0.08 USD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.submitJob() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = uiState.selectedVideo != null && !uiState.isSubmitting
                            ) {
                                if (uiState.isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Subir y Escalar con GPU")
                                }
                            }
                        }
                    }
                }
            }

            // Lista de Trabajos de Escalado Activos y Pasados
            item {
                Text(
                    text = "Historial de Trabajos IA",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (upscaleJobs.isEmpty()) {
                item {
                    Text(
                        text = "No has ejecutado ningún trabajo de escalado aún.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(upscaleJobs, key = { it.id }) { job ->
                    UpscaleJobCard(
                        job = job,
                        onPlay = {
                            job.resultFilePath?.let { path ->
                                navController.navigate(Screen.VideoPlayer.createRoute(Uri.encode(path)))
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal para seleccionar video
    if (showVideoPicker) {
        AlertDialog(
            onDismissRequest = { showVideoPicker = false },
            title = { Text("Elige un video para escalar") },
            text = {
                if (availableVideos.isEmpty()) {
                    Text("No tienes videos descargados en la biblioteca aún.")
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(availableVideos) { vid ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectVideo(vid)
                                        showVideoPicker = false
                                    }
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = vid.title,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoPicker = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
fun UpscaleJobCard(
    job: UpscaleJobEntity,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Escalado ${job.targetResolution} @ ${job.targetFps}fps",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Proveedor: ${job.provider} • Estado: ${job.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (job.status == "SUCCEEDED" && job.resultFilePath != null) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { job.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
