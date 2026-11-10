package com.mphone.ui.components

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricTeal
import com.mphone.ui.theme.MissedRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VoiceRecordingWavePlayer(
    file: File,
    modifier: Modifier = Modifier,
    onDeleteFile: (file: File) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxDragPx = with(density) { 80.dp.toPx() }
    val dragOffsetX = remember { Animatable(0f) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    currentPositionMs = mp.currentPosition
                    durationMs = mp.duration
                }
            }
            delay(200L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Background Actions (Left = Delete Red, Right = Share Indigo)
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swipe Right -> Delete File
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Recording", tint = MissedRed)
                Text("Delete", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MissedRed)
            }

            // Swipe Left -> Share File
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Share", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ElectricIndigo)
                Icon(Icons.Default.Share, contentDescription = "Share Recording", tint = ElectricIndigo)
            }
        }

        // Foreground Card Player (Compact Modern Sound Wave Row)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newOffset = (dragOffsetX.value + delta).coerceIn(-maxDragPx, maxDragPx)
                            dragOffsetX.snapTo(newOffset)
                        }
                    },
                    onDragStopped = {
                        if (dragOffsetX.value <= -maxDragPx * 0.6f) {
                            shareAudioFile(context, file)
                        } else if (dragOffsetX.value >= maxDragPx * 0.6f) {
                            showDeleteDialog = true
                        }
                        scope.launch {
                            dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Play / Pause Circle Button
                FilledIconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer?.pause()
                            isPlaying = false
                        } else {
                            try {
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            if (mediaPlayer == null) {
                                val mp = MediaPlayer().apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    setDataSource(file.absolutePath)
                                    setVolume(1.0f, 1.0f)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        currentPositionMs = 0
                                    }
                                }
                                mediaPlayer = mp
                                durationMs = mp.duration
                            } else {
                                mediaPlayer?.start()
                            }
                            isPlaying = true
                        }
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ElectricIndigo,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause Recording",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Compact Sound Wave Visualizer in Center
                Box(modifier = Modifier.weight(1f)) {
                    SoundWaveBarVisualizer(isPlaying = isPlaying)
                }

                // Time Duration / Position Label
                Text(
                    text = if (isPlaying) formatTime(currentPositionMs) else if (durationMs > 0) formatTime(durationMs) else "${file.length() / 1024}KB",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricIndigo
                )
            }
        }

        // Delete Recording Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Voice Recording") },
                text = { Text("Are you sure you want to delete this voice recording?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                            try {
                                if (file.exists()) {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            onDeleteFile(file)
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun shareAudioFile(context: Context, file: File) {
    try {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Voice Recording"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun SoundWaveBarVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val numBars = 18
    val heights = remember { List(numBars) { Animatable((10..28).random().toFloat()) } }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                heights.forEach { anim ->
                    val target = (8..30).random().toFloat()
                    anim.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = (150..350).random(), easing = LinearEasing)
                    )
                }
            }
        } else {
            heights.forEachIndexed { index, anim ->
                val base = if (index % 3 == 0) 16f else 8f
                anim.animateTo(base, tween(300))
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { index, anim ->
            val barColor = if (index % 2 == 0) ElectricIndigo else ElectricTeal
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(anim.value.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) barColor else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}
