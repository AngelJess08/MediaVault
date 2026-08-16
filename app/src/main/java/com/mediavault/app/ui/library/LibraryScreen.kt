package com.mediavault.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
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
import com.mediavault.storage.db.entity.DownloadEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavHostController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca y Archivos", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = "Cambiar vista"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Barra de Búsqueda
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Buscar en biblioteca...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filtros por Pestañas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.ALL,
                    onClick = { viewModel.setFilter(LibraryFilter.ALL) },
                    label = { Text("Todo") }
                )
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.VIDEOS,
                    onClick = { viewModel.setFilter(LibraryFilter.VIDEOS) },
                    label = { Text("Videos") },
                    leadingIcon = { Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.AUDIOS,
                    onClick = { viewModel.setFilter(LibraryFilter.AUDIOS) },
                    label = { Text("Audios") },
                    leadingIcon = { Icon(Icons.Outlined.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.UPSCALED,
                    onClick = { viewModel.setFilter(LibraryFilter.UPSCALED) },
                    label = { Text("Escalados IA") },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.FAVORITES,
                    onClick = { viewModel.setFilter(LibraryFilter.FAVORITES) },
                    label = { Text("Favoritos") },
                    leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.selectedFilter == LibraryFilter.TRASH,
                    onClick = { viewModel.setFilter(LibraryFilter.TRASH) },
                    label = { Text("Papelera") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No se encontraron archivos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (uiState.isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(downloads, key = { it.id }) { item ->
                            LibraryGridItem(
                                item = item,
                                onClick = { openPlayer(navController, item) },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onMoveToTrash = { viewModel.moveToTrash(item.id) },
                                onRestore = { viewModel.restoreFromTrash(item.id) },
                                onPermanentDelete = { viewModel.permanentDelete(item.id) },
                                onShare = { shareMedia(context, item) },
                                onUpscale = { navController.navigate(Screen.Upscale.route) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(downloads, key = { it.id }) { item ->
                            LibraryListItem(
                                item = item,
                                onClick = { openPlayer(navController, item) },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onMoveToTrash = { viewModel.moveToTrash(item.id) },
                                onRestore = { viewModel.restoreFromTrash(item.id) },
                                onPermanentDelete = { viewModel.permanentDelete(item.id) },
                                onShare = { shareMedia(context, item) },
                                onUpscale = { navController.navigate(Screen.Upscale.route) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openPlayer(navController: NavHostController, item: DownloadEntity) {
    val encoded = Uri.encode(item.filePath)
    if (item.type == "AUDIO") {
        navController.navigate(Screen.AudioPlayer.createRoute(encoded))
    } else {
        navController.navigate(Screen.VideoPlayer.createRoute(encoded))
    }
}

private fun shareMedia(context: android.content.Context, item: DownloadEntity) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (item.type == "AUDIO") "audio/*" else "video/*"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(item.filePath))
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(Intent.createChooser(intent, "Compartir con"))
}

@Composable
fun LibraryListItem(
    item: DownloadEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onShare: () -> Unit,
    onUpscale: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.type == "AUDIO") Icons.Filled.Audiotrack else Icons.Filled.Movie,
                contentDescription = null,
                tint = if (item.type == "AUDIO") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.platform} • ${item.format} • ${item.fileSize / (1024 * 1024)}MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (item.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Reproducir") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = { showMenu = false; onClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { showMenu = false; onShare() }
                    )
                    if (item.type == "VIDEO" && !item.inTrash) {
                        DropdownMenuItem(
                            text = { Text("Escalar con IA") },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            onClick = { showMenu = false; onUpscale() }
                        )
                    }
                    if (item.inTrash) {
                        DropdownMenuItem(
                            text = { Text("Restaurar") },
                            leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                            onClick = { showMenu = false; onRestore() }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar definitivamente") },
                            leadingIcon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
                            onClick = { showMenu = false; onPermanentDelete() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Mover a Papelera") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { showMenu = false; onMoveToTrash() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryGridItem(
    item: DownloadEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onShare: () -> Unit,
    onUpscale: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.type == "AUDIO") Icons.Filled.Audiotrack else Icons.Filled.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.platform} • ${item.fileSize / (1024 * 1024)}MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
