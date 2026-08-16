package com.mediavault.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mediavault.app.navigation.Screen
import com.mediavault.app.ui.cookies.CookieLoginDialog
import com.mediavault.downloader.model.FormatOption
import com.mediavault.downloader.model.Platform
import com.mediavault.storage.db.entity.DownloadEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentDownloads by viewModel.recentDownloads.collectAsState()
    val context = LocalContext.current
    var showAdvancedOptions by remember { mutableStateOf(false) }

    var webViewLoginTarget: Pair<String, String>? by remember { mutableStateOf(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.showSuccessMessage) {
        uiState.showSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MediaVault",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (uiState.isIncognito) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Incógnito",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleIncognito(!uiState.isIncognito) }) {
                        Icon(
                            imageVector = if (uiState.isIncognito) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "Modo incógnito"
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Ajustes"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de Extracción Principal
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isBatchMode) "Descarga por Lote" else "Descargador Universal",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            TextButton(onClick = { viewModel.toggleBatchMode(!uiState.isBatchMode) }) {
                                Text(if (uiState.isBatchMode) "Modo Simple" else "Modo Lote")
                            }
                        }

                        if (!uiState.isBatchMode) {
                            OutlinedTextField(
                                value = uiState.urlInput,
                                onValueChange = { viewModel.onUrlChanged(it) },
                                label = { Text("Pega el enlace del video o audio") },
                                placeholder = { Text("YouTube, Instagram, TikTok, Twitter/X...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                trailingIcon = {
                                    if (uiState.urlInput.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.pasteFromClipboard() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pegar")
                                }

                                FilledTonalButton(
                                    onClick = { viewModel.analyzeUrl() },
                                    enabled = uiState.urlInput.isNotBlank() && !uiState.isLoading,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Analizar")
                                    }
                                }
                            }

                            // Badge de Plataforma Detectada
                            if (uiState.detectedPlatform != Platform.UNKNOWN && uiState.detectedPlatform != Platform.GENERIC) {
                                Spacer(modifier = Modifier.height(10.dp))
                                PlatformBadge(platform = uiState.detectedPlatform)
                            }
                        } else {
                            // Modo Lote
                            OutlinedTextField(
                                value = uiState.batchUrlsText,
                                onValueChange = { viewModel.onBatchUrlsChanged(it) },
                                label = { Text("Pega múltiples URLs (una por línea)") },
                                placeholder = { Text("https://...\nhttps://...\nhttps://...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                shape = RoundedCornerShape(14.dp),
                                maxLines = 6
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.startBatchDownload() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = uiState.batchUrlsText.isNotBlank()
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descargar Todo el Lote")
                            }
                        }
                    }
                }
            }

            // AVISO DE DETECCIÓN DE DUPLICADOS (Función 6)
            uiState.duplicateExistingDownload?.let { dup ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Este video ya fue descargado previamente",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Archivo: ${dup.title} (${dup.format})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.dismissDuplicatePrompt() }) {
                                    Text("Descargar de nuevo")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (dup.type == "AUDIO") {
                                            navController.navigate(Screen.AudioPlayer.createRoute(Uri.encode(dup.filePath)))
                                        } else {
                                            navController.navigate(Screen.VideoPlayer.createRoute(Uri.encode(dup.filePath)))
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Abrir Existente")
                                }
                            }
                        }
                    }
                }
            }

            // Mensaje de Error y Botón de Inicio de Sesión si es requerido
            uiState.errorMessage?.let { error ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (uiState.isLoginRequiredError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val (targetUrl, name) = when (uiState.detectedPlatform) {
                                            Platform.TWITTER -> "https://x.com/login" to "Twitter / X"
                                            Platform.INSTAGRAM -> "https://www.instagram.com/accounts/login/" to "Instagram"
                                            Platform.FACEBOOK -> "https://m.facebook.com/login/" to "Facebook"
                                            else -> "https://accounts.google.com/" to "YouTube"
                                        }
                                        webViewLoginTarget = targetUrl to name
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Iniciar sesión para descargar este contenido")
                                }
                            }
                        }
                    }
                }
            }

            // Información de Medios y Selector Dinámico de Formatos Nativos
            uiState.mediaInfo?.let { info ->
                item {
                    MediaPreviewAndFormatPicker(
                        mediaInfo = info,
                        uiState = uiState,
                        onFormatSelected = { viewModel.setSelectedFormat(it) },
                        onAudioSelected = { f, b -> viewModel.setSelectedAudio(f, b) },
                        onToggleAudioOnly = { viewModel.toggleAudioOnly(it) },
                        onToggleThumbnailOnly = { viewModel.toggleThumbnailOnly(it) },
                        onToggleBurnSubtitles = { viewModel.toggleBurnSubtitles(it) },
                        onSelectSubtitle = { viewModel.setSelectedSubtitle(it) },
                        onSetTrim = { s, e -> viewModel.setTrim(s, e) },
                        onSetScheduledDelay = { viewModel.setScheduledDelayMinutes(it) },
                        onSetSpeedLimit = { viewModel.setSpeedLimit(it) },
                        onToggleWifiOnly = { viewModel.toggleWifiOnly(it) },
                        onDownloadClicked = { viewModel.startDownload() },
                        showAdvanced = showAdvancedOptions,
                        onToggleAdvanced = { showAdvancedOptions = !showAdvancedOptions }
                    )
                }
            }

            // Descargas Recientes
            if (recentDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Descargas Recientes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(recentDownloads.take(5)) { item ->
                    RecentDownloadItemCard(
                        item = item,
                        onClick = {
                            if (item.type == "AUDIO") {
                                navController.navigate(Screen.AudioPlayer.createRoute(Uri.encode(item.filePath)))
                            } else {
                                navController.navigate(Screen.VideoPlayer.createRoute(Uri.encode(item.filePath)))
                            }
                        },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (item.type == "AUDIO") "audio/*" else "video/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.filePath))
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir con"))
                        }
                    )
                }
            }
        }
    }

    // WebView Dialog para Login
    webViewLoginTarget?.let { (url, platform) ->
        CookieLoginDialog(
            initialUrl = url,
            platformName = platform,
            onDismiss = { webViewLoginTarget = null },
            onCookiesCaptured = { currentUrl, cookies ->
                viewModel.analyzeUrl()
                webViewLoginTarget = null
            }
        )
    }
}

@Composable
fun PlatformBadge(platform: Platform) {
    val (color, name) = when (platform) {
        Platform.YOUTUBE -> Color(0xFFFF0000) to "YouTube"
        Platform.INSTAGRAM -> Color(0xFFE1306C) to "Instagram"
        Platform.TIKTOK -> Color(0xFF000000) to "TikTok"
        Platform.FACEBOOK -> Color(0xFF1877F2) to "Facebook"
        Platform.TWITTER -> Color(0xFF1DA1F2) to "Twitter / X"
        Platform.REDDIT -> Color(0xFFFF4500) to "Reddit"
        Platform.TWITCH -> Color(0xFF9146FF) to "Twitch"
        Platform.SOUNDCLOUD -> Color(0xFFFF5500) to "SoundCloud"
        Platform.VIMEO -> Color(0xFF1AB7EA) to "Vimeo"
        Platform.PINTEREST -> Color(0xFFBD081C) to "Pinterest"
        else -> MaterialTheme.colorScheme.primary to platform.name
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Plataforma: $name",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewAndFormatPicker(
    mediaInfo: com.mediavault.downloader.model.MediaInfo,
    uiState: HomeUiState,
    onFormatSelected: (FormatOption) -> Unit,
    onAudioSelected: (String, String) -> Unit,
    onToggleAudioOnly: (Boolean) -> Unit,
    onToggleThumbnailOnly: (Boolean) -> Unit,
    onToggleBurnSubtitles: (Boolean) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onSetTrim: (Long?, Long?) -> Unit,
    onSetScheduledDelay: (Long) -> Unit,
    onSetSpeedLimit: (Int) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onDownloadClicked: () -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Vista previa del video
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (!mediaInfo.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = mediaInfo.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp, 70.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaInfo.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mediaInfo.uploader ?: "Autor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Duración: ${mediaInfo.duration / 60}m ${mediaInfo.duration % 60}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selector de tipo (Video con Audio / Solo Audio / Solo Portada)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !uiState.isAudioOnly && !uiState.isThumbnailOnly,
                    onClick = {
                        onToggleAudioOnly(false)
                        onToggleThumbnailOnly(false)
                    },
                    label = { Text("Video Completo") },
                    leadingIcon = { Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.isAudioOnly && !uiState.isThumbnailOnly,
                    onClick = {
                        onToggleAudioOnly(true)
                        onToggleThumbnailOnly(false)
                    },
                    label = { Text("Solo Audio") },
                    leadingIcon = { Icon(Icons.Outlined.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.isThumbnailOnly,
                    onClick = {
                        onToggleThumbnailOnly(true)
                        onToggleAudioOnly(false)
                    },
                    label = { Text("Solo Carátula") },
                    leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!uiState.isThumbnailOnly && !uiState.isAudioOnly) {
                // Selector de Calidades Nativas Reales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calidades Nativas Disponibles",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "NATIVO",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val videoFormats = mediaInfo.formats.filter { !it.isAudioOnly }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(videoFormats) { fmt ->
                        val isSelected = uiState.selectedFormatId == fmt.formatId
                        val sizeMb = (fmt.filesize ?: fmt.filesizeApprox ?: 0L) / (1024 * 1024)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onFormatSelected(fmt) }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = fmt.resolution ?: "Auto",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${fmt.fps?.toInt() ?: 30}fps • ${if (sizeMb > 0) "${sizeMb}MB" else "~Est."}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isAudioOnly) {
                Text(
                    text = "Formato de Audio y Bitrate",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    val audioOptions = listOf(
                        "mp3" to "320 kbps",
                        "m4a" to "256 kbps",
                        "opus" to "160 kbps",
                        "flac" to "Lossless",
                        "original" to "Sin Recodificar"
                    )
                    audioOptions.forEach { (fmt, bitrate) ->
                        val isSelected = uiState.selectedAudioFormat == fmt
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onAudioSelected(fmt, bitrate) }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = fmt.uppercase(),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = bitrate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Opciones Avanzadas (Recorte, Subtítulos, Programación, Wi-Fi)
            TextButton(
                onClick = onToggleAdvanced,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showAdvanced) "Ocultar Opciones Avanzadas" else "Opciones Avanzadas (Programar, Subtítulos, Wi-Fi...)")
            }

            if (showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Subtítulos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Incrustar Subtítulos (Hardsub)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = uiState.burnSubtitles,
                            onCheckedChange = { onToggleBurnSubtitles(it) }
                        )
                    }

                    // Solo Wi-Fi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Descargar solo con Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = uiState.wifiOnly,
                            onCheckedChange = { onToggleWifiOnly(it) }
                        )
                    }

                    // Descarga Programada (Madrugada o Delay)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Programar descarga (Ahorro / Noche)", style = MaterialTheme.typography.bodyMedium)
                        FilterChip(
                            selected = uiState.scheduledDelayMinutes > 0,
                            onClick = { onSetScheduledDelay(if (uiState.scheduledDelayMinutes > 0) 0 else 180) },
                            label = { Text(if (uiState.scheduledDelayMinutes > 0) "+3 Horas" else "Inmediata") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Botón de Iniciar Descarga
            Button(
                onClick = onDownloadClicked,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isThumbnailOnly) "Guardar Carátula" else if (uiState.isAudioOnly) "Descargar Audio" else "Descargar Calidad Nativa (${uiState.selectedResolution})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun RecentDownloadItemCard(
    item: DownloadEntity,
    onClick: () -> Unit,
    onShare: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.type == "AUDIO") Icons.Filled.Audiotrack else Icons.Filled.Movie,
                contentDescription = null,
                tint = if (item.type == "AUDIO") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.platform} • ${item.format} • ${item.fileSize / (1024 * 1024)}MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
            }
        }
    }
}
