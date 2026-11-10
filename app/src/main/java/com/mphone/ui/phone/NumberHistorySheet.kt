package com.mphone.ui.phone

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mphone.data.ContactMerger
import com.mphone.model.CallLogItem
import com.mphone.model.CallType
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.components.VoiceRecordingWavePlayer
import com.mphone.ui.theme.IncomingGreen
import com.mphone.ui.theme.MissedRed
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberHistorySheet(
    name: String,
    number: String,
    historyLogs: List<CallLogItem>,
    onDismiss: () -> Unit,
    onCallClick: () -> Unit,
    onSmsClick: () -> Unit,
    onDeleteLogs: () -> Unit,
    onDeleteSingleLog: (id: String) -> Unit = {},
    onBlockNumber: () -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }

    // Find voice recordings for this number or name
    var recordings by remember(number, name) {
        mutableStateOf(getRecordingsForNumber(context, name, number))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactAvatar(
                name = name.ifBlank { number },
                size = 80.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = name.ifBlank { number },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = number,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilledIconButton(
                    onClick = {
                        onDismiss()
                        onCallClick()
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call")
                }

                FilledIconButton(
                    onClick = {
                        onDismiss()
                        onSmsClick()
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "SMS")
                }

                FilledIconButton(
                    onClick = { showBlockConfirmation = true },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MissedRed.copy(alpha = 0.2f),
                        contentColor = MissedRed
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Block, contentDescription = "Block")
                }

                FilledIconButton(
                    onClick = { showDeleteConfirmation = true },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Voice Recordings Section with Sound Wave Animations
            if (recordings.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Voice Recordings (${recordings.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    recordings.forEach { file ->
                        VoiceRecordingWavePlayer(
                            file = file,
                            onDeleteFile = { deleted ->
                                recordings = recordings.filter { it.absolutePath != deleted.absolutePath }
                            }
                        )
                    }
                }
            }

            // Call History Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Call History (${historyLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (historyLogs.isEmpty()) {
                    Text(
                        text = "No history records found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(historyLogs, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val (icon, color) = when (item.type) {
                                        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to IncomingGreen
                                        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.colorScheme.primary
                                        CallType.MISSED, CallType.REJECTED -> Icons.AutoMirrored.Filled.CallMissed to MissedRed
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = item.timestamp,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { onDeleteSingleLog(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Single Log",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showBlockConfirmation) {
            AlertDialog(
                onDismissRequest = { showBlockConfirmation = false },
                title = { Text("Block Number") },
                text = { Text("Are you sure you want to block $name ($number)? All call history for this number will be moved to the Spam page.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBlockConfirmation = false
                            onDismiss()
                            onBlockNumber()
                        }
                    ) {
                        Text("Block", color = MissedRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Call History") },
                text = { Text("Are you sure you want to delete all call logs for $name?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            onDismiss()
                            onDeleteLogs()
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun getRecordingsForNumber(context: Context, name: String, number: String): List<File> {
    val dirs = listOf(
        File(context.getExternalFilesDir(null), "CallRecordings"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "CallRecordings")
    )

    val allFiles = mutableListOf<File>()
    dirs.forEach { dir ->
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.let { allFiles.addAll(it) }
        }
    }

    if (allFiles.isEmpty()) return emptyList()

    val cleanNum = ContactMerger.normalizePhoneNumber(number)
    val shortClean = if (cleanNum.length >= 7) cleanNum.takeLast(7) else ""
    val digitsOnly = number.replace(Regex("[^0-9]"), "")
    val safeName = name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

    return allFiles.filter { file ->
        val fileName = file.name.lowercase()
        val numMatch = (cleanNum.isNotBlank() && fileName.contains(cleanNum)) ||
                (shortClean.isNotBlank() && fileName.contains(shortClean)) ||
                (digitsOnly.length >= 7 && fileName.contains(digitsOnly))

        val nameMatch = safeName.isNotBlank() &&
                safeName != "unknown" &&
                safeName != digitsOnly.lowercase() &&
                fileName.contains(safeName)

        numMatch || nameMatch
    }.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
}
