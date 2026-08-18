package com.mediavault.app.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mediavault.downloader.security.AdAndMalwareFilter
import com.mediavault.downloader.security.SecureWebViewHelper
import com.mediavault.downloader.universal.SnifferCandidate
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    navController: NavHostController,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val blockedAdsCount by viewModel.blockedAdsCount.collectAsState()
    val context = LocalContext.current

    var webViewInstance: WebView? by remember { mutableStateOf(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showAdBlockInfoDialog by remember { mutableStateOf(false) }

    // Manejo del botón físico Atrás para historial del WebView
    BackHandler(enabled = uiState.canGoBack) {
        webViewInstance?.goBack()
    }

    LaunchedEffect(uiState.downloadSuccessMessage) {
        uiState.downloadSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                // Barra de herramientas superior
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { webViewInstance?.goBack() },
                            enabled = uiState.canGoBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = uiState.canGoForward,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Adelante", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = {
                                if (uiState.isLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (uiState.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                                contentDescription = if (uiState.isLoading) "Detener" else "Recargar",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Campo de texto de URL o Búsqueda
                        OutlinedTextField(
                            value = uiState.inputUrl,
                            onValueChange = { viewModel.onInputUrlChanged(it) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .padding(horizontal = 4.dp),
                            placeholder = { Text("Buscar o ingresar URL...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = if (uiState.isSslSecure && uiState.currentUrl.startsWith("https")) Icons.Filled.Lock else Icons.Outlined.Public,
                                    contentDescription = null,
                                    tint = if (uiState.isSslSecure && uiState.currentUrl.startsWith("https")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.inputUrl.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onInputUrlChanged("") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Borrar", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                viewModel.submitUrlOrQuery(uiState.inputUrl)
                                webViewInstance?.loadUrl(uiState.currentUrl)
                            })
                        )

                        // Badge de bloqueo de anuncios y amenazas
                        Surface(
                            onClick = { showAdBlockInfoDialog = true },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Shield,
                                    contentDescription = "Anuncios bloqueados",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$blockedAdsCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Barra de progreso lineal
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        progress = { uiState.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Banner de aviso de modo navegador
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Modo Navegador Seguro: Sandbox activo y bloqueo de popups",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Botón flotante para descargar streams descubiertos
            AnimatedVisibility(
                visible = uiState.detectedCandidates.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.toggleCandidatesSheet(true) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    icon = {
                        BadgedBox(
                            badge = {
                                Badge { Text("${uiState.detectedCandidates.size}") }
                            }
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                        }
                    },
                    text = { Text("Descargar (${uiState.detectedCandidates.size})", fontWeight = FontWeight.Bold) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Pantalla de inicio si la URL está vacía o es about:blank
            if (uiState.currentUrl.isEmpty() || uiState.currentUrl == "about:blank") {
                BrowserHomeScreen(
                    onShortcutSelected = { url ->
                        viewModel.submitUrlOrQuery(url)
                        webViewInstance?.loadUrl(url)
                    },
                    onSearch = { query ->
                        viewModel.submitUrlOrQuery(query)
                        webViewInstance?.loadUrl(uiState.currentUrl)
                    }
                )
            }

            // 2. WebView Integrado con Hardening de Seguridad
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Hardening de seguridad integral
                        SecureWebViewHelper.applyStrictSecuritySettings(settings, isVisibleBrowser = true)

                        val redirectTracker = SecureWebViewHelper.RedirectTracker()

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                viewModel.onProgressChanged(newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                viewModel.onPageFinished(
                                    url = view?.url ?: "",
                                    title = title,
                                    canBack = view?.canGoBack() ?: false,
                                    canForward = view?.canGoForward() ?: false
                                )
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                Timber.tag("MediaVaultBrowser").d("Popup bloqueado en navegador visible.")
                                return false // Bloquear popups espontáneos
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false

                                // A.1: Bloqueo de Intent Hijacking y esquemas no-web con confirmación obligatoria
                                if (!SecureWebViewHelper.isSafeWebScheme(url)) {
                                    Timber.tag("MediaVaultBrowser").w("Esquema no-web interceptado en navegador: $url")
                                    viewModel.promptExternalIntent(url)
                                    return true
                                }

                                // A.2: Detección de Redirect-Bombing
                                if (redirectTracker.recordAndCheckBombing()) {
                                    viewModel.onRedirectBombingDetected()
                                    view?.stopLoading()
                                    return true
                                }

                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                                // A.8 & A.9: Bloqueo de anuncios, rastreadores y malware a nivel de red
                                if (com.mediavault.downloader.security.AdAndMalwareFilter().shouldBlock(reqUrl)) {
                                    return com.mediavault.downloader.security.AdAndMalwareFilter().createEmptyResponse()
                                }

                                // Sniffer pasivo de streams de video y audio en segundo plano
                                evaluateBrowserStream(reqUrl, request.requestHeaders) { candidate ->
                                    viewModel.addDiscoveredCandidate(candidate)
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                url?.let { viewModel.onPageStarted(it) }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                url?.let {
                                    viewModel.onPageFinished(
                                        url = it,
                                        title = view?.title,
                                        canBack = view?.canGoBack() ?: false,
                                        canForward = view?.canGoForward() ?: false
                                    )
                                }
                                // Inyectar CSS anti-intersticiales
                                SecureWebViewHelper.injectAntiInterstitialScript(this@apply)
                            }
                        }

                        webViewInstance = this
                    }
                },
                update = { webView ->
                    if (uiState.currentUrl.isNotEmpty() && uiState.currentUrl != "about:blank" && webView.url != uiState.currentUrl && !uiState.isLoading) {
                        webView.loadUrl(uiState.currentUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (uiState.currentUrl.isEmpty() || uiState.currentUrl == "about:blank") 0f else 1f)
            )
        }
    }

    // ModalBottomSheet con candidatos de descarga detectados
    if (uiState.showCandidatesSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleCandidatesSheet(false) },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Videos Detectados en la Página",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${uiState.detectedCandidates.size} stream(s) disponible(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.toggleCandidatesSheet(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.detectedCandidates) { candidate ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (candidate.isAudioOnly) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (candidate.isAudioOnly) Icons.Filled.Audiotrack else Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = if (candidate.isAudioOnly) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = candidate.estimatedResolution ?: (if (candidate.isAudioOnly) "Pista de Audio" else "Video HD"),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Formato: ${candidate.extension.uppercase()} ${if (candidate.isHls) "• Stream HLS" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.enqueueCandidateDownload(candidate) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Descargar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de Confirmación de Intent Externo (A.1)
    uiState.pendingExternalIntent?.let { intentUrl ->
        val appDescription = SecureWebViewHelper.parseIntentDescription(intentUrl)
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingIntent() },
            icon = { Icon(Icons.Filled.Launch, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Abrir aplicación externa") },
            text = {
                Text("Este sitio solicita abrir $appDescription fuera de MediaVault. Por tu seguridad, ¿deseas permitir esta acción?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.tag("MediaVaultBrowser").e(e, "No se pudo abrir la app para $intentUrl")
                        }
                        viewModel.dismissPendingIntent()
                    }
                ) {
                    Text("Permitir")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingIntent() }) {
                    Text("Bloquear")
                }
            }
        )
    }

    // Diálogo de Alerta de Redirect-Bombing (A.2)
    if (uiState.redirectBombWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.clearRedirectWarning() },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Redirección agresiva bloqueada") },
            text = {
                Text("Se detectó un patrón de redirecciones múltiples en cadena típico de publicidad o malware. La carga fue detenida para proteger tu dispositivo.")
            },
            confirmButton = {
                Button(onClick = { viewModel.clearRedirectWarning() }) {
                    Text("Entendido")
                }
            }
        )
    }

    // Diálogo de Información de Bloqueo de Anuncios y Telemetría
    if (showAdBlockInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAdBlockInfoDialog = false },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Protección de Red Activa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛡️ Total bloqueados en esta sesión: $blockedAdsCount")
                    Text("Se han bloqueado solicitudes hacia dominios conocidos de publicidad invasiva, rastreadores analíticos, cryptominers y popups maliciosos.")
                    Text(
                        "Nota: Reduce la gran mayoría de la publicidad y scripts de terceros, aunque no garantiza el 100% en sitios con ofuscación compleja.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdBlockInfoDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun BrowserHomeScreen(
    onShortcutSelected: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val shortcuts = listOf(
        Triple("YouTube", "https://m.youtube.com", Color(0xFFFF0000)),
        Triple("TikTok", "https://www.tiktok.com", Color(0xFF000000)),
        Triple("Instagram", "https://www.instagram.com", Color(0xFFE1306C)),
        Triple("Twitter / X", "https://x.com", Color(0xFF1DA1F2)),
        Triple("Reddit", "https://www.reddit.com", Color(0xFFFF4500)),
        Triple("Vimeo", "https://vimeo.com", Color(0xFF1AB7EA)),
        Triple("SoundCloud", "https://soundcloud.com", Color(0xFFFF5500)),
        Triple("Twitch", "https://m.twitch.tv", Color(0xFF9146FF))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Navegador Web Integrado",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Explora la web y captura videos directamente mientras navegas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar en DuckDuckGo o ingresar URL...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(searchQuery) })
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Sitios Populares",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(shortcuts) { (name, url, color) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShortcutSelected(url) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = color.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Evalúa peticiones interceptadas en el navegador en tiempo real para encontrar streams.
 */
private fun evaluateBrowserStream(
    url: String,
    headers: Map<String, String>?,
    onFound: (SnifferCandidate) -> Unit
) {
    val lower = url.lowercase()
    if (lower.startsWith("blob:") || lower.contains("widevine") || lower.contains("/ad/") || lower.contains("analytics")) {
        return
    }

    if (lower.contains(".m3u8")) {
        onFound(
            SnifferCandidate(
                url = url,
                mimeType = "application/vnd.apple.mpegurl",
                extension = "m3u8",
                isHls = true,
                estimatedResolution = "Auto (Stream HLS)",
                headers = headers ?: emptyMap()
            )
        )
        return
    }

    if (lower.contains(".mpd")) {
        onFound(
            SnifferCandidate(
                url = url,
                mimeType = "application/dash+xml",
                extension = "mpd",
                isDash = true,
                estimatedResolution = "Auto (Stream DASH)",
                headers = headers ?: emptyMap()
            )
        )
        return
    }

    val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".mov")
    val matchVideo = videoExtensions.find { lower.contains(it) }
    if (matchVideo != null) {
        val ext = matchVideo.removePrefix(".")
        onFound(
            SnifferCandidate(
                url = url,
                mimeType = "video/$ext",
                extension = ext,
                isHls = false,
                isAudioOnly = false,
                estimatedResolution = if (lower.contains("1080")) "1080p FHD" else if (lower.contains("720")) "720p HD" else "Video Web",
                headers = headers ?: emptyMap()
            )
        )
        return
    }

    val audioExtensions = listOf(".mp3", ".m4a", ".aac", ".ogg", ".flac")
    val matchAudio = audioExtensions.find { lower.contains(it) }
    if (matchAudio != null) {
        val ext = matchAudio.removePrefix(".")
        onFound(
            SnifferCandidate(
                url = url,
                mimeType = "audio/$ext",
                extension = ext,
                isHls = false,
                isAudioOnly = true,
                estimatedResolution = "Audio ($ext)",
                headers = headers ?: emptyMap()
            )
        )
    }
}
