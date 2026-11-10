package com.mphone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mphone.data.SmsSenderId
import com.mphone.ui.theme.IncomingGreen
import com.mphone.ui.theme.MissedRed

sealed class DynamicIslandState {
    data object Idle : DynamicIslandState()

    data class ActiveCall(
        val name: String,
        val number: String,
        val durationStr: String,
        val isMuted: Boolean = false
    ) : DynamicIslandState()

    data class SmsAlert(
        val senderName: String,
        val senderNumber: String,
        val messageSnippet: String,
        val threadId: String = ""
    ) : DynamicIslandState()

    data class SpamBlockedAlert(
        val title: String,
        val reason: String
    ) : DynamicIslandState()
}

@Composable
fun DynamicIslandNotification(
    state: DynamicIslandState,
    modifier: Modifier = Modifier,
    onExpandCall: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onOpenSms: () -> Unit = {},
    onQuickReply: (number: String, message: String) -> Unit = { _, _ -> },
    onMarkAsRead: (threadId: String) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var isExpanded by remember(state) { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state !is DynamicIslandState.Idle,
        enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0F172A), // Dark slate pill background
            contentColor = Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessLow))
        ) {
            when (state) {
                is DynamicIslandState.ActiveCall -> {
                    ActiveCallIslandContent(
                        call = state,
                        isExpanded = isExpanded,
                        onExpandCall = onExpandCall,
                        onEndCall = onEndCall,
                        onToggleMute = onToggleMute
                    )
                }

                is DynamicIslandState.SmsAlert -> {
                    SmsAlertIslandContent(
                        alert = state,
                        onOpenSms = onOpenSms,
                        onQuickReply = onQuickReply,
                        onMarkAsRead = onMarkAsRead,
                        onDismiss = onDismiss
                    )
                }

                is DynamicIslandState.SpamBlockedAlert -> {
                    SpamBlockedIslandContent(
                        alert = state,
                        onDismiss = onDismiss
                    )
                }

                DynamicIslandState.Idle -> {}
            }
        }
    }
}

@Composable
private fun ActiveCallIslandContent(
    call: DynamicIslandState.ActiveCall,
    isExpanded: Boolean,
    onExpandCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit
) {
    if (!isExpanded) {
        // Compact Pill Mode - Tap anywhere to return to full screen call
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandCall() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(IncomingGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Active Call",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = call.name.ifBlank { call.number },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to Return to Call",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }

            Text(
                text = call.durationStr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = IncomingGreen
            )
        }
    } else {
        // Expanded Mode
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(name = call.name, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(call.name.ifBlank { call.number }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(call.number, fontSize = 12.sp, color = Color.LightGray)
                    }
                }
                Text(call.durationStr, fontWeight = FontWeight.Bold, color = IncomingGreen, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (call.isMuted) MaterialTheme.colorScheme.primary else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MissedRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsAlertIslandContent(
    alert: DynamicIslandState.SmsAlert,
    onOpenSms: () -> Unit,
    onQuickReply: (number: String, message: String) -> Unit,
    onMarkAsRead: (threadId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val canReply = remember(alert.senderNumber, alert.senderName) {
        !SmsSenderId.isBrandSenderId(alert.senderNumber.ifBlank { alert.senderName })
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isReplying) {
        if (isReplying) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onDismiss()
                        onOpenSms()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = "SMS",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = alert.senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (alert.senderNumber.isNotBlank() && alert.senderNumber != alert.senderName) {
                        Text(
                            text = alert.senderNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = alert.messageSnippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Actions: Quick Reply Button & Mark as Read Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Reply Icon Button (Left) - hidden for brand senders
                if (canReply) {
                    IconButton(
                        onClick = { isReplying = !isReplying },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Quick Reply",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Mark as Read Icon Button (Right)
                IconButton(
                    onClick = {
                        onMarkAsRead(alert.threadId.ifBlank { alert.senderNumber })
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(IncomingGreen.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Mark as Read",
                        tint = IncomingGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Expanded Inline Quick Reply Text Field
        if (isReplying && canReply) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Send quick message...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            onQuickReply(alert.senderNumber.ifBlank { alert.senderName }, replyText.trim())
                            replyText = ""
                            isReplying = false
                            onDismiss()
                        }
                    },
                    enabled = replyText.isNotBlank(),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (replyText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else Color(0xFF334155)
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Answer",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpamBlockedIslandContent(
    alert: DynamicIslandState.SpamBlockedAlert,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MissedRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Spam Blocked",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = alert.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.LightGray, modifier = Modifier.size(18.dp))
        }
    }
}
