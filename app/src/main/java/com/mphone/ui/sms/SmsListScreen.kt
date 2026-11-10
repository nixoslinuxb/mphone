package com.mphone.ui.sms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mphone.data.MockData
import com.mphone.model.SmsThread
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.theme.MissedRed
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SmsListScreen(
    threads: List<SmsThread>,
    searchQuery: String,
    onThreadClick: (SmsThread) -> Unit,
    onNewMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
    spamThreads: List<SmsThread> = emptyList(),
    onUnblockSpamThread: (threadId: String) -> Unit = {},
    onBlockThread: (threadId: String) -> Unit = {},
    onDeleteThread: (threadId: String) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var showSpamSheet by remember { mutableStateOf(false) }

    var threadToBlock by remember { mutableStateOf<SmsThread?>(null) }
    var threadToDelete by remember { mutableStateOf<SmsThread?>(null) }

    val filteredThreads = remember(threads, searchQuery, selectedFilter) {
        threads.filter { thread ->
            val hasMessages = thread.messages.isNotEmpty() || thread.lastMessage.isNotBlank()

            val matchesQuery = searchQuery.isBlank() ||
                    thread.contactName.contains(searchQuery, ignoreCase = true) ||
                    thread.contactNumber.contains(searchQuery, ignoreCase = true) ||
                    thread.lastMessage.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Unread" -> thread.unreadCount > 0
                else -> true
            }

            hasMessages && matchesQuery && matchesFilter
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Google Messages Category Filter Bar & Spam Icon Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Unread",
                        onClick = { selectedFilter = "Unread" },
                        label = { Text("Unread") }
                    )
                }

                // Top Right Spam & Blocked Messages Button
                IconButton(onClick = { showSpamSheet = true }) {
                    if (spamThreads.isNotEmpty()) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MissedRed,
                                    contentColor = Color.White
                                ) {
                                    Text(spamThreads.size.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Spam & Blocked Messages",
                                tint = MissedRed
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Spam & Blocked Messages",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Message Threads List
            if (filteredThreads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredThreads, key = { it.id }) { thread ->
                        SweepableGoogleMessagesThreadItem(
                            thread = thread,
                            onClick = { onThreadClick(thread) },
                            onBlockClick = { threadToBlock = thread },
                            onDeleteClick = { threadToDelete = thread }
                        )
                    }
                }
            }
        }

        // Modern Floating Action Button: New Message Logo
        FloatingActionButton(
            onClick = onNewMessageClick,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(60.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = "New Message",
                modifier = Modifier.size(28.dp)
            )
        }

        // Spam & Blocked Messages Sheet
        if (showSpamSheet) {
            SpamMessagesSheet(
                spamThreads = spamThreads,
                onDismiss = { showSpamSheet = false },
                onUnblockThread = { threadId ->
                    onUnblockSpamThread(threadId)
                }
            )
        }

        // Block Thread Confirmation Dialog
        val targetBlock = threadToBlock
        if (targetBlock != null) {
            AlertDialog(
                onDismissRequest = { threadToBlock = null },
                title = { Text("Block Number") },
                text = { Text("Are you sure you want to block ${targetBlock.contactName} (${targetBlock.contactNumber})? Future messages will be moved to the Spam page.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onBlockThread(targetBlock.id)
                            threadToBlock = null
                        }
                    ) {
                        Text("Block", color = MissedRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { threadToBlock = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Thread Confirmation Dialog
        val targetDelete = threadToDelete
        if (targetDelete != null) {
            AlertDialog(
                onDismissRequest = { threadToDelete = null },
                title = { Text("Delete Message History") },
                text = { Text("Are you sure you want to delete message history for ${targetDelete.contactName}? This will delete all messages completely from phone memory.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteThread(targetDelete.id)
                            threadToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { threadToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SweepableGoogleMessagesThreadItem(
    thread: SmsThread,
    onClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxDragPx = with(density) { 90.dp.toPx() }
    val dragOffsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Background Actions (Swipe Right = Delete Red, Swipe Left = Block Red)
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swipe Right Action -> Delete History
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                Text("Delete", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }

            // Swipe Left Action -> Block Number
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Block", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MissedRed)
                Icon(Icons.Default.Block, contentDescription = "Block", tint = MissedRed)
            }
        }

        // Foreground Card Item
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (thread.unreadCount > 0)
                    MaterialTheme.colorScheme.surfaceContainerHigh
                else
                    MaterialTheme.colorScheme.surfaceContainer
            ),
            onClick = onClick,
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
                        if (dragOffsetX.value >= maxDragPx * 0.6f) {
                            onDeleteClick() // Swipe Right -> Delete
                        } else if (dragOffsetX.value <= -maxDragPx * 0.6f) {
                            onBlockClick() // Swipe Left -> Block
                        } else if (abs(dragOffsetX.value) < maxDragPx * 0.25f) {
                            onClick()
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
                    .clickable { onClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (thread.unreadCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(thread.unreadCount.toString())
                            }
                        }
                    ) {
                        ContactAvatar(
                            name = thread.contactName,
                            photoUri = thread.photoUri,
                            colorIndex = thread.avatarColorIndex,
                            size = 52.dp
                        )
                    }
                } else {
                    ContactAvatar(
                        name = thread.contactName,
                        photoUri = thread.photoUri,
                        colorIndex = thread.avatarColorIndex,
                        size = 52.dp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = thread.contactName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = thread.timestamp,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (thread.unreadCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = thread.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (thread.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (thread.unreadCount > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SmsListScreenPreview() {
    MphoneTheme {
        SmsListScreen(
            threads = MockData.sampleSmsThreads,
            searchQuery = "",
            onThreadClick = {},
            onNewMessageClick = {}
        )
    }
}
