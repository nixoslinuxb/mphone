package com.mphone.ui.phone

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mphone.data.ContactMerger
import com.mphone.data.MockData
import com.mphone.model.CallLogItem
import com.mphone.model.CallType
import com.mphone.model.Contact
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.sms.SimSelectionDialog
import com.mphone.ui.sms.getActiveSimSlots
import com.mphone.ui.theme.IncomingGreen
import com.mphone.ui.theme.MissedRed
import com.mphone.ui.theme.MphoneTheme
import com.mphone.ui.theme.OutgoingBlue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ConsolidatedCallLog(
    val primaryLog: CallLogItem,
    val totalCount: Int,
    val allLogs: List<CallLogItem>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneScreen(
    callLogs: List<CallLogItem>,
    searchQuery: String,
    onStartCall: (name: String, number: String, subId: Int?) -> Unit,
    modifier: Modifier = Modifier,
    spamCallLogs: List<CallLogItem> = emptyList(),
    blockedNumbers: Set<String> = emptySet(),
    contacts: List<Contact> = emptyList(),
    hasUnreadSpamCalls: Boolean = false,
    hasUnreadMissedCalls: Boolean = false,
    onMarkSpamCallsAsRead: () -> Unit = {},
    onMarkMissedCallsAsRead: () -> Unit = {},
    onDeleteCallLog: (id: String) -> Unit = {},
    onDeleteCallLogsForNumber: (number: String, contactId: String?) -> Unit = { _, _ -> },
    onBlockNumber: (number: String) -> Unit = {},
    onUnblockNumber: (number: String) -> Unit = {},
    onAddContactForNumber: (number: String) -> Unit = {},
    onSmsClick: (number: String) -> Unit = {}
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val simSlots = remember { getActiveSimSlots(context) }

    var showDialpad by remember { mutableStateOf(false) }
    var dialNumber by remember { mutableStateOf("") }
    val matchedDialContact = remember(dialNumber, contacts) {
        if (dialNumber.isNotBlank()) ContactMerger.findContactForNumber(contacts, dialNumber) else null
    }
    var selectedFilter by remember { mutableStateOf("All") }
    var showSpamCallsSheet by remember { mutableStateOf(false) }
    var showBlockedSheet by remember { mutableStateOf(false) }
    var showSimCallSelectionDialog by remember { mutableStateOf(false) }

    // Dialog / Sheet States
    var selectedLogForHistory by remember { mutableStateOf<CallLogItem?>(null) }
    var logToDelete by remember { mutableStateOf<CallLogItem?>(null) }

    LaunchedEffect(selectedFilter) {
        if (selectedFilter == "Missed") {
            onMarkMissedCallsAsRead()
        }
    }

    val filteredLogs = remember(callLogs, searchQuery, selectedFilter) {
        callLogs.filter { log ->
            val matchesQuery = searchQuery.isBlank() ||
                    log.name.contains(searchQuery, ignoreCase = true) ||
                    log.phoneNumber.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Missed" -> log.type == CallType.MISSED || log.type == CallType.REJECTED
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    // Group logs by person/number so that multiple calls from the same caller show as 1 consolidated row
    val consolidatedLogs = remember(filteredLogs) {
        val groups = linkedMapOf<String, MutableList<CallLogItem>>()
        filteredLogs.forEach { log ->
            val cleanNumber = ContactMerger.normalizePhoneNumber(log.phoneNumber)
            val personKey = log.contactId ?: cleanNumber.ifBlank { log.name }
            groups.getOrPut(personKey) { mutableListOf() }.add(log)
        }
        groups.values.map { personLogs ->
            ConsolidatedCallLog(
                primaryLog = personLogs.first(),
                totalCount = personLogs.size,
                allLogs = personLogs
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Filter Chips & Top Right Spam/Block Icon Buttons
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
                        label = { Text("All Calls") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Missed",
                        onClick = {
                            selectedFilter = "Missed"
                            onMarkMissedCallsAsRead()
                        },
                        label = { Text("Missed") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Top Right Spam Icon Button
                    IconButton(onClick = {
                        onMarkSpamCallsAsRead()
                        showSpamCallsSheet = true
                    }) {
                        if (hasUnreadSpamCalls && spamCallLogs.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MissedRed,
                                        contentColor = Color.White
                                    ) {
                                        Text(spamCallLogs.size.toString())
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Spam Calls",
                                    tint = MissedRed
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Spam Calls",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Top Right Block Logo Button (Right of Spam Logo)
                    IconButton(onClick = { showBlockedSheet = true }) {
                        if (blockedNumbers.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MissedRed,
                                        contentColor = Color.White
                                    ) {
                                        Text(blockedNumbers.size.toString())
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = "Blocked Numbers",
                                    tint = MissedRed
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Blocked Numbers",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Call Logs List
            if (consolidatedLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent calls",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(consolidatedLogs, key = { it.primaryLog.id }) { item ->
                        SweepableCallLogListItem(
                            log = item.primaryLog,
                            callCount = item.totalCount,
                            showCallCount = hasUnreadMissedCalls,
                            onItemClick = { selectedLogForHistory = item.primaryLog },
                            onCopyNumberClick = {
                                clipboardManager.setText(AnnotatedString(item.primaryLog.phoneNumber))
                                Toast.makeText(context, "Copied ${item.primaryLog.phoneNumber} to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onCallClick = {
                                val sim1SubId = simSlots.firstOrNull { it.slotIndex == 0 }?.subscriptionId
                                    ?: simSlots.firstOrNull()?.subscriptionId
                                onStartCall(item.primaryLog.name, item.primaryLog.phoneNumber, sim1SubId)
                            },
                            onDeleteClick = { logToDelete = item.primaryLog },
                            onAddContactClick = { onAddContactForNumber(item.primaryLog.phoneNumber) },
                            onSmsClick = { onSmsClick(item.primaryLog.phoneNumber) }
                        )
                    }
                }
            }
        }

        // T9 Dialer Overlay
        AnimatedVisibility(
            visible = showDialpad,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Row with Close Button (Paste icon removed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showDialpad = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Dialpad")
                        }
                    }

                    // Dial Number Input Field with Press & Hold (Long Click) to Paste Number
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        val clipboardText = clipboardManager.getText()?.text
                                        if (!clipboardText.isNullOrBlank()) {
                                            val cleanedNumber = clipboardText.replace(Regex("[^0-9+*#]"), "")
                                            if (cleanedNumber.isNotBlank()) {
                                                dialNumber = cleanedNumber
                                                Toast.makeText(context, "Pasted $cleanedNumber", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "No valid phone number in clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                        ) {
                            Text(
                                text = dialNumber.ifBlank { "Enter Number" },
                                fontSize = if (dialNumber.length > 12) 24.sp else 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dialNumber.isBlank()) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            if (matchedDialContact != null) {
                                Text(
                                    text = matchedDialContact.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        if (dialNumber.isNotEmpty()) {
                            IconButton(onClick = { dialNumber = dialNumber.dropLast(1) }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val keypadKeys = listOf(
                        "1" to "", "2" to "ABC", "3" to "DEF",
                        "4" to "GHI", "5" to "JKL", "6" to "MNO",
                        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
                        "*" to "", "0" to "+", "#" to ""
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        keypadKeys.chunked(3).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { (digit, letters) ->
                                    KeypadButton(
                                        digit = digit,
                                        letters = letters,
                                        onClick = { dialNumber += digit },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Call Button supporting Short Click (SIM 1) and Press & Hold (Select SIM)
                    Surface(
                        shape = CircleShape,
                        color = IncomingGreen,
                        contentColor = Color.White,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .then(
                                if (dialNumber.isNotBlank()) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            // Short Tap: Call using SIM 1
                                            val sim1SubId = simSlots.firstOrNull { it.slotIndex == 0 }?.subscriptionId
                                                ?: simSlots.firstOrNull()?.subscriptionId
                                            val targetName = matchedDialContact?.name ?: dialNumber
                                            onStartCall(targetName, dialNumber, sim1SubId)
                                            showDialpad = false
                                            dialNumber = ""
                                        },
                                        onLongClick = {
                                            // Press & Hold: Choose which SIM to call
                                            if (simSlots.size > 1) {
                                                showSimCallSelectionDialog = true
                                            } else {
                                                val sim1SubId = simSlots.firstOrNull()?.subscriptionId
                                                val targetName = matchedDialContact?.name ?: dialNumber
                                                onStartCall(targetName, dialNumber, sim1SubId)
                                                showDialpad = false
                                                dialNumber = ""
                                                Toast.makeText(context, "Calling via SIM 1 (Default)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                } else Modifier
                            )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call (Tap: SIM 1, Hold: Choose SIM)",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // Keypad FAB Toggle Button when hidden
        if (!showDialpad) {
            FloatingActionButton(
                onClick = { showDialpad = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Dialpad, contentDescription = "Open Dialpad")
            }
        }

        // Dual-SIM Call Selection Dialog
        if (showSimCallSelectionDialog) {
            SimSelectionDialog(
                simSlots = simSlots,
                onDismiss = { showSimCallSelectionDialog = false },
                onSimSelected = { selectedSubId ->
                    if (dialNumber.isNotBlank()) {
                        val targetName = matchedDialContact?.name ?: dialNumber
                        onStartCall(targetName, dialNumber, selectedSubId)
                        showDialpad = false
                        dialNumber = ""
                    }
                }
            )
        }

        // Number History Bottom Sheet
        val currentHistoryLog = selectedLogForHistory
        if (currentHistoryLog != null) {
            val numLogs = remember(currentHistoryLog, callLogs) {
                val cleanTarget = ContactMerger.normalizePhoneNumber(currentHistoryLog.phoneNumber)
                val targetContactId = currentHistoryLog.contactId
                callLogs.filter {
                    (targetContactId != null && it.contactId == targetContactId) ||
                            ContactMerger.normalizePhoneNumber(it.phoneNumber) == cleanTarget
                }
            }
            NumberHistorySheet(
                name = currentHistoryLog.name,
                number = currentHistoryLog.phoneNumber,
                historyLogs = numLogs,
                onDismiss = { selectedLogForHistory = null },
                onCallClick = {
                    val sim1SubId = simSlots.firstOrNull { it.slotIndex == 0 }?.subscriptionId
                        ?: simSlots.firstOrNull()?.subscriptionId
                    onStartCall(currentHistoryLog.name, currentHistoryLog.phoneNumber, sim1SubId)
                },
                onSmsClick = { onSmsClick(currentHistoryLog.phoneNumber) },
                onDeleteLogs = {
                    onDeleteCallLogsForNumber(currentHistoryLog.phoneNumber, currentHistoryLog.contactId)
                    selectedLogForHistory = null
                },
                onDeleteSingleLog = { id ->
                    onDeleteCallLog(id)
                },
                onBlockNumber = {
                    onBlockNumber(currentHistoryLog.phoneNumber)
                    selectedLogForHistory = null
                }
            )
        }

        // Spam Calls Sheet
        if (showSpamCallsSheet) {
            SpamCallsSheet(
                spamLogs = spamCallLogs,
                onDismiss = { showSpamCallsSheet = false },
                onUnblockNumber = { number ->
                    onUnblockNumber(number)
                }
            )
        }

        // Blocked Numbers Sheet
        if (showBlockedSheet) {
            BlockedNumbersSheet(
                blockedNumbers = blockedNumbers,
                contacts = contacts,
                onDismiss = { showBlockedSheet = false },
                onUnblockNumber = { number ->
                    onUnblockNumber(number)
                }
            )
        }

        // Delete Call Log Confirmation Alert Dialog
        val targetDeleteLog = logToDelete
        if (targetDeleteLog != null) {
            AlertDialog(
                onDismissRequest = { logToDelete = null },
                title = { Text("Delete Call Log") },
                text = { Text("Are you sure you want to delete all call logs for ${targetDeleteLog.name}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteCallLogsForNumber(targetDeleteLog.phoneNumber, targetDeleteLog.contactId)
                            logToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { logToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SweepableCallLogListItem(
    log: CallLogItem,
    callCount: Int,
    showCallCount: Boolean,
    onItemClick: () -> Unit,
    onCopyNumberClick: () -> Unit,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onSmsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxDragPx = with(density) { 90.dp.toPx() }
    val dragOffsetX = remember { Animatable(0f) }

    val icon = when (log.type) {
        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallType.MISSED, CallType.REJECTED -> Icons.Default.CallMissed
    }

    val iconColor = when (log.type) {
        CallType.INCOMING -> IncomingGreen
        CallType.OUTGOING -> OutgoingBlue
        CallType.MISSED, CallType.REJECTED -> MissedRed
    }

    val displayName = if (callCount > 1 && showCallCount) "${log.name} ($callCount)" else log.name
    val isNonContact = log.contactId == null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Background Actions (Left = Call Green, Right = Delete Red)
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swipe Right Action -> Call (Green)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = IncomingGreen)
                Text("Call", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = IncomingGreen)
            }

            // Swipe Left Action -> Delete (Red)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Delete", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MissedRed)
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MissedRed)
            }
        }

        // Foreground Card Item
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onItemClick,
                    onLongClick = onCopyNumberClick
                )
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
                            onCallClick()
                        } else if (dragOffsetX.value <= -maxDragPx * 0.6f) {
                            onDeleteClick()
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(
                    name = log.name,
                    photoUri = log.photoUri,
                    size = 48.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (log.type == CallType.MISSED) MissedRed else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${log.phoneNumber} • ${log.timestamp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isNonContact) {
                        IconButton(
                            onClick = onAddContactClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Add Contact",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onSmsClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "Text Message",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.height(60.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = digit,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneScreenPreview() {
    MphoneTheme {
        PhoneScreen(
            callLogs = MockData.sampleCallLogs,
            searchQuery = "",
            onStartCall = { _, _, _ -> }
        )
    }
}
