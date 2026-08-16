package com.mediavault.app.ui.player

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun AudioPlayerScreen(
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }
    var showEqualizer by remember { mutableStateOf(false) }
    var bassBoost by remember { mutableStateOf(0.5f) }
    var trebleBoost by remember { mutableStateOf(0.5f) }
    var sleepTimerMinutes by remember { mutableStateOf(0) }

    val exoPlayer = remember(context, filePath) {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse(filePath)
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(1L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            kotlinx.coroutines.delay(500)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                }
                Text("Reproductor de Audio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showEqualizer = !showEqualizer }) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = "Ecualizador")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Carátula de Álbum
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Título
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = filePath.substringAfterLast("/").substringBeforeLast("."),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "MediaVault Audio Player",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Visualizador de Onda de Audio Animado
            AudioWaveform(isPlaying = isPlaying)

            // Barra de Progreso y Tiempo
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { percent ->
                        val target = (percent * duration).toLong()
                        exoPlayer.seekTo(target)
                        currentPosition = target
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Controles de Reproducción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0L)) }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Retroceder 10s", modifier = Modifier.size(32.dp))
                }

                FilledIconButton(
                    onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir/Pausar",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { exoPlayer.seekTo((currentPosition + 10000).coerceAtMost(duration)) }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Adelantar 10s", modifier = Modifier.size(32.dp))
                }
            }

            // Sleep Timer Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable {
                    sleepTimerMinutes = when (sleepTimerMinutes) {
                        0 -> 15
                        15 -> 30
                        30 -> 60
                        else -> 0
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (sleepTimerMinutes > 0) "Sleep Timer: ${sleepTimerMinutes}m" else "Sleep Timer: Apagado",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // Modal de Ecualizador Básico
    if (showEqualizer) {
        AlertDialog(
            onDismissRequest = { showEqualizer = false },
            title = { Text("Ecualizador Básico") },
            text = {
                Column {
                    Text("Graves (Bass Boost): ${(bassBoost * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(value = bassBoost, onValueChange = { bassBoost = it })

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Agudos (Treble): ${(trebleBoost * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(value = trebleBoost, onValueChange = { trebleBoost = it })
                }
            },
            confirmButton = {
                TextButton(onClick = { showEqualizer = false }) { Text("Listo") }
            }
        )
    }
}

@Composable
fun AudioWaveform(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = (1..18).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = if (isPlaying) 1.0f else 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + (i * 45), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$i"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(h.value.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
