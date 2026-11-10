package com.mphone.ui.sms

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.mphone.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mphone.data.MockData
import com.mphone.data.SendStatus
import com.mphone.data.SmsSenderId
import com.mphone.model.CallLogItem
import com.mphone.model.SmsMessage
import com.mphone.model.SmsThread
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricViolet
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    thread: SmsThread,
    callLogs: List<CallLogItem> = emptyList(),
    allThreads: List<SmsThread> = emptyList(),
    isSavedContact: Boolean = true,
    onBackClick: () -> Unit,
    onCallClick: () -> Unit,
    onSelectThread: (SmsThread) -> Unit = {},
    modifier: Modifier = Modifier,
    onSaveContactClick: () -> Unit = {},
    onDeleteMessage: (SmsMessage) -> Unit = {},
    onSendMessage: (text: String, subId: Int?) -> SendStatus
) {
    val context = LocalContext.current
    val isBrandThread = remember(thread.contactNumber, thread.id) {
        SmsSenderId.isBrandSenderId(thread.contactNumber.ifBlank { thread.id })
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Local state for displayed messages
    var displayedMessages by remember(thread.id, thread.messages) {
        mutableStateOf(thread.messages)
    }

    LaunchedEffect(thread.messages) {
        displayedMessages = thread.messages
    }

    val simSlots = remember { getActiveSimSlots(context) }
    var showSimSelectionDialog by remember { mutableStateOf(false) }
    var showPersonDetailSheet by remember { mutableStateOf(false) }
    var msgToDelete by remember { mutableStateOf<SmsMessage?>(null) }
    var pendingSendText by remember { mutableStateOf("") }

    // Message Vaporize Animation State
    var vaporizingMsgId by remember { mutableStateOf<String?>(null) }
    val vaporizeProgress = remember { Animatable(0f) }

    // Intercept System Back Button & System Edge Swipes
    BackHandler(enabled = true) {
        onBackClick()
    }

    // Real-Time 3D Page Turn Sweep Right Back State
    val dragX = remember { Animatable(0f) }
    val swipeBackThresholdPx = with(density) { 85.dp.toPx() }
    val leftEdgeThresholdPx = with(density) { 90.dp.toPx() }
    val isPageClosing = remember { mutableStateOf(false) }
    var isEdgeDrag by remember { mutableStateOf(false) }

    // Flying Letter & Vaporize Particle Animation State
    var isFlyingLetterActive by remember { mutableStateOf(false) }
    val flyAnimProgress = remember { Animatable(0f) }

    LaunchedEffect(isFlyingLetterActive) {
        if (isFlyingLetterActive) {
            flyAnimProgress.snapTo(0f)
            flyAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = LinearEasing)
            )
            isFlyingLetterActive = false
        }
    }

    LaunchedEffect(thread.messages.size) {
        if (thread.messages.isNotEmpty()) {
            listState.animateScrollToItem(thread.messages.size - 1)
        }
    }

    val validThreads = remember(allThreads, thread) {
        val list = allThreads.filter { it.messages.isNotEmpty() || it.lastMessage.isNotBlank() }
        if (list.any { it.id == thread.id }) list else listOf(thread) + list
    }

    val currentIndex = remember(thread.id, validThreads) {
        val idx = validThreads.indexOfFirst { it.id == thread.id }
        if (idx >= 0) idx else 0
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val dragFraction = (dragX.value / screenWidthPx).coerceIn(-1f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dragX.value
                    rotationY = dragFraction * -42f // Real-time 3D page curl tilt
                    scaleX = 1f - (abs(dragFraction) * 0.12f)
                    scaleY = 1f - (abs(dragFraction) * 0.12f)
                    alpha = (1f - abs(dragFraction) * 0.35f).coerceIn(0f, 1f)
                    cameraDistance = 16f * density.density
                    transformOrigin = if (dragX.value >= 0f) {
                        TransformOrigin(0f, 0.5f) // Left spine pivot
                    } else {
                        TransformOrigin(1f, 0.5f) // Right spine pivot
                    }
                }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showPersonDetailSheet = true }
                            ) {
                                ContactAvatar(
                                    name = thread.contactName,
                                    photoUri = thread.photoUri,
                                    colorIndex = thread.avatarColorIndex,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = thread.contactName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${thread.messages.size} messages • ${thread.contactNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            if (!isSavedContact) {
                                IconButton(onClick = onSaveContactClick) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Save Contact",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = onCallClick) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                },
                bottomBar = {
                    if (isBrandThread) {
                        BrandReplyBlockedBar()
                    } else {
                    // Text Message Input Area with Hold & Sweep Left/Right 3D Page Turn Gesture
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .pointerInput(thread.id) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        if (!isPageClosing.value) {
                                            change.consume()
                                            scope.launch {
                                                dragX.snapTo(dragX.value + dragAmount.x)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (!isPageClosing.value) {
                                            val dx = dragX.value
                                            if (dx < -swipeBackThresholdPx && validThreads.size > 1) {
                                                // Sweep Left -> Turn 3D Page to NEXT Message History!
                                                isPageClosing.value = true
                                                val nextIndex = (currentIndex + 1) % validThreads.size
                                                val nextThread = validThreads[nextIndex]
                                                scope.launch {
                                                    dragX.animateTo(
                                                        targetValue = -screenWidthPx * 1.1f,
                                                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                    )
                                                    onSelectThread(nextThread)
                                                    dragX.snapTo(0f)
                                                    isPageClosing.value = false
                                                }
                                            } else if (dx > swipeBackThresholdPx && validThreads.size > 1) {
                                                // Sweep Right -> Turn 3D Page to PREVIOUS Message History!
                                                isPageClosing.value = true
                                                val prevIndex = Math.floorMod(currentIndex - 1, validThreads.size)
                                                val prevThread = validThreads[prevIndex]
                                                scope.launch {
                                                    dragX.animateTo(
                                                        targetValue = screenWidthPx * 1.1f,
                                                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                    )
                                                    onSelectThread(prevThread)
                                                    dragX.snapTo(0f)
                                                    isPageClosing.value = false
                                                }
                                            } else if (abs(dx) > swipeBackThresholdPx && validThreads.size <= 1) {
                                                // Single thread fallback -> Back to messages
                                                isPageClosing.value = true
                                                scope.launch {
                                                    val target = if (dx > 0) screenWidthPx * 1.1f else -screenWidthPx * 1.1f
                                                    dragX.animateTo(
                                                        targetValue = target,
                                                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                    )
                                                    onBackClick()
                                                }
                                            } else {
                                                scope.launch {
                                                    dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Text message (SMS)") },
                            singleLine = false,
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            trailingIcon = {
                                if (inputText.isBlank()) {
                                    IconButton(
                                        onClick = {
                                            val clipText = clipboardManager.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                inputText = clipText
                                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            val clipText = clipboardManager.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                inputText += clipText
                                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                        )

                        // Send Button Surface supporting Short Tap (SIM 1) and Long Press (Select SIM)
                        Surface(
                            shape = CircleShape,
                            color = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(
                                    if (inputText.isNotBlank()) {
                                        Modifier.combinedClickable(
                                            onClick = {
                                                // Short Tap: Send message using SIM 1
                                                val sim1SubId = simSlots.firstOrNull { it.slotIndex == 0 }?.subscriptionId
                                                    ?: simSlots.firstOrNull()?.subscriptionId
                                                val status = onSendMessage(inputText.trim(), sim1SubId)
                                                when (status) {
                                                    is SendStatus.Success -> {
                                                        inputText = ""
                                                        isFlyingLetterActive = true
                                                    }
                                                    is SendStatus.RateLimited -> {
                                                        Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                                                    }
                                                    is SendStatus.DuplicateBlocked -> {
                                                        Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                                                    }
                                                    is SendStatus.BrandBlocked -> {
                                                        Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                // Press and Hold: Ask which SIM to use
                                                if (simSlots.size > 1) {
                                                    pendingSendText = inputText.trim()
                                                    showSimSelectionDialog = true
                                                } else {
                                                    Toast.makeText(context, "Using SIM 1 (Default)", Toast.LENGTH_SHORT).show()
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
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message (Tap: SIM 1, Hold: Choose SIM)",
                                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                    }
                },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            // Chat Messages Content Area with 3D Page Turn Sweep Right Back Gesture
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val screenWidthPx = with(density) { maxWidth.toPx() }
                val dragFraction = (dragX.value / screenWidthPx).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = dragX.value
                            rotationY = dragFraction * -38f // Real-time 3D page curl tilt
                            scaleX = 1f - (dragFraction * 0.12f)
                            scaleY = 1f - (dragFraction * 0.12f)
                            alpha = (1f - dragFraction * 0.4f).coerceIn(0f, 1f)
                            cameraDistance = 16f * density.density
                            transformOrigin = TransformOrigin(0f, 0.5f) // Left spine pivot
                        }
                        .pointerInput(thread.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isEdgeDrag = offset.x <= leftEdgeThresholdPx
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (isEdgeDrag && !isPageClosing.value) {
                                        change.consume()
                                        scope.launch {
                                            val newX = (dragX.value + dragAmount).coerceAtLeast(0f)
                                            dragX.snapTo(newX)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (isEdgeDrag && !isPageClosing.value) {
                                        if (dragX.value > swipeBackThresholdPx) {
                                            // Sweep Right on left edge -> 3D Page Turn Completion Back
                                            isPageClosing.value = true
                                            scope.launch {
                                                dragX.animateTo(
                                                    targetValue = screenWidthPx * 1.1f,
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                )
                                                onBackClick()
                                            }
                                        } else {
                                            scope.launch {
                                                dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                            }
                                        }
                                    }
                                    isEdgeDrag = false
                                }
                            )
                        }
                ) {
                    // Full-Screen pcm App Logo Background Photo
                    ChatAppLogoBackground()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedMessages, key = { "${it.id}_${it.timestamp}_${it.body.hashCode()}" }) { msg ->
                            val isVaporizingThis = msg.id == vaporizingMsgId
                            ChatMessageBubble(
                                msg = msg,
                                isVaporizing = isVaporizingThis,
                                vaporizeProgressFloat = if (isVaporizingThis) vaporizeProgress.value else 0f,
                                onDeleteClick = {
                                    msgToDelete = msg
                                },
                                onCopyText = {
                                    clipboardManager.setText(AnnotatedString(msg.body))
                                    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onCopyLink = { url ->
                                    clipboardManager.setText(AnnotatedString(url))
                                    Toast.makeText(context, "Link copied to clipboard: $url", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Full Screen Flying Letter & Center Vaporize Particle Animation Overlay
        val flyProgress = flyAnimProgress.value
        if (flyProgress > 0f && flyProgress < 1f) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidthPx = with(density) { maxWidth.toPx() }
                val screenHeightPx = with(density) { maxHeight.toPx() }

                val startX = screenWidthPx - with(density) { 36.dp.toPx() }
                val startY = screenHeightPx - with(density) { 68.dp.toPx() }

                val targetX = screenWidthPx / 2f
                val targetY = screenHeightPx / 2f

                if (flyProgress <= 0.5f) {
                    // Phase 1: Flight from Send Button to Screen Center
                    val flightProgress = (flyProgress / 0.5f).coerceIn(0f, 1f)
                    val currentX = startX + (targetX - startX) * flightProgress
                    val currentY = startY + (targetY - startY) * flightProgress
                    val currentScale = 0.8f + 0.6f * flightProgress
                    val currentRotation = -28f * (1f - flightProgress)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset((currentX - with(density) { 24.dp.toPx() }).roundToInt(), (currentY - with(density) { 24.dp.toPx() }).roundToInt()) }
                            .scale(currentScale)
                            .rotate(currentRotation)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ElectricIndigo)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    // Phase 2: Vaporize Disintegration & Energy Particle Burst at Center
                    val vaporizeProgress = ((flyProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    val currentScale = 1.4f + 0.8f * vaporizeProgress
                    val currentAlpha = (1f - vaporizeProgress * 1.2f).coerceIn(0f, 1f)

                    // Central Vaporizing Letter Icon
                    if (currentAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((targetX - with(density) { 24.dp.toPx() }).roundToInt(), (targetY - with(density) { 24.dp.toPx() }).roundToInt()) }
                                .scale(currentScale)
                                .graphicsLayer { alpha = currentAlpha }
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(ElectricIndigo, ElectricViolet)
                                    )
                                )
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailRead,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Radiating Vaporize Energy Particles
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val particleCount = 16
                        val maxRadius = 130.dp.toPx()
                        val particleColors = listOf(
                            ElectricIndigo,
                            ElectricViolet,
                            Color(0xFF10B981),
                            Color(0xFFF59E0B),
                            Color(0xFF06B6D4)
                        )

                        for (i in 0 until particleCount) {
                            val angle = (i * (2 * Math.PI / particleCount)).toFloat()
                            val dist = maxRadius * vaporizeProgress
                            val px = targetX + cos(angle) * dist
                            val py = targetY + sin(angle) * dist
                            val particleRadius = (6.dp.toPx() * (1f - vaporizeProgress)).coerceAtLeast(1f)
                            val particleAlpha = (1f - vaporizeProgress).coerceIn(0f, 1f)
                            val color = particleColors[i % particleColors.size].copy(alpha = particleAlpha)

                            drawCircle(
                                color = color,
                                radius = particleRadius,
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
        }

        // Dual-SIM Selection Dialog
            if (showSimSelectionDialog) {
                SimSelectionDialog(
                    simSlots = simSlots,
                    onDismiss = { showSimSelectionDialog = false },
                    onSimSelected = { selectedSubId ->
                        val status = onSendMessage(pendingSendText, selectedSubId)
                        when (status) {
                            is SendStatus.Success -> {
                                inputText = ""
                                pendingSendText = ""
                                isFlyingLetterActive = true
                            }
                            is SendStatus.RateLimited -> {
                                Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                            }
                            is SendStatus.DuplicateBlocked -> {
                                Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                            }
                            is SendStatus.BrandBlocked -> {
                                Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // Chat Contact Detail Sheet
            if (showPersonDetailSheet) {
                ChatContactDetailSheet(
                    thread = thread,
                    callLogs = callLogs,
                    isSavedContact = isSavedContact,
                    onDismiss = { showPersonDetailSheet = false },
                    onCallClick = onCallClick,
                    onSaveContactClick = onSaveContactClick
                )
            }

            // Delete Single Message Confirmation Alert Dialog
            val targetDeleteMsg = msgToDelete
            if (targetDeleteMsg != null) {
                AlertDialog(
                    onDismissRequest = { msgToDelete = null },
                    title = { Text("Delete Message") },
                    text = { Text("Are you sure you want to delete this message?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val targetMsg = targetDeleteMsg
                                msgToDelete = null

                                // 1. Instant local UI update: remove only this message
                                displayedMessages = displayedMessages.filterNot { msg ->
                                    msg.id == targetMsg.id &&
                                        msg.timestamp == targetMsg.timestamp &&
                                        msg.body == targetMsg.body
                                }

                                // 2. Persistent repository deletion
                                onDeleteMessage(targetMsg)
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Yes", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { msgToDelete = null }) {
                            Text("No")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BrandReplyBlockedBar() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Marka mesajlarına yanıt gönderilemez.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatAppLogoBackground() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(340.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricIndigo.copy(alpha = 0.35f),
                            ElectricViolet.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "pcm App Logo",
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer { alpha = 0.45f },
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "pcm",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    msg: SmsMessage,
    isVaporizing: Boolean = false,
    vaporizeProgressFloat: Float = 0f,
    onDeleteClick: () -> Unit,
    onCopyText: () -> Unit,
    onCopyLink: (url: String) -> Unit
) {
    val isUser = msg.isFromUser

    // Cartoonish Asymmetric Comic Bubble Shape
    val cartoonBubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (isUser) 24.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 24.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .graphicsLayer {
                if (isVaporizing) {
                    alpha = (1f - vaporizeProgressFloat * 1.2f).coerceIn(0f, 1f)
                    scaleX = 1f + vaporizeProgressFloat * 0.30f
                    scaleY = 1f + vaporizeProgressFloat * 0.30f
                    rotationZ = vaporizeProgressFloat * 8f
                }
            },
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // Foreground Cartoon Message Bubble Container
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (!isVaporizing) onDeleteClick()
                    }
                )
        ) {
            // 2.5D Cartoon Pop Drop Shadow Layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.5.dp, y = 3.5.dp)
                    .clip(cartoonBubbleShape)
                    .background(Color(0xFF0F172A))
            )

            // Main Cartoon Message Surface Card
            Surface(
                shape = cartoonBubbleShape,
                color = if (isUser) Color(0xFF6366F1) else Color(0xFF1E293B),
                contentColor = Color.White,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isUser) Color(0xFF4338CA) else Color(0xFF3B82F6)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .pointerInput(msg.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onCopyText()
                                },
                                onLongPress = {
                                    onDeleteClick()
                                }
                            )
                        }
                ) {
                    ClickableLinkMessageText(
                        body = msg.body,
                        isUser = isUser,
                        onLinkClick = onCopyLink,
                        onDoubleTap = onCopyText,
                        onLongPress = onDeleteClick
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = msg.timestamp,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isUser) Color.White.copy(alpha = 0.8f) else Color.LightGray
                        )
                        if (isUser) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "SMS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Radiating Energy Particles Burst Effect on Vaporize
        if (isVaporizing && vaporizeProgressFloat > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val particleCount = 20
                val maxRadius = size.width * 0.6f
                val centerX = if (isUser) size.width * 0.8f else size.width * 0.2f
                val centerY = size.height * 0.5f

                val particleColors = listOf(
                    ElectricIndigo,
                    ElectricViolet,
                    Color(0xFFEF4444),
                    Color(0xFFF59E0B),
                    Color(0xFF06B6D4)
                )

                for (i in 0 until particleCount) {
                    val angle = (i * (2 * Math.PI / particleCount)).toFloat()
                    val dist = maxRadius * vaporizeProgressFloat
                    val px = centerX + cos(angle) * dist
                    val py = centerY + sin(angle) * dist
                    val particleRadius = (7.dp.toPx() * (1f - vaporizeProgressFloat)).coerceAtLeast(1f)
                    val particleAlpha = (1f - vaporizeProgressFloat * 1.1f).coerceIn(0f, 1f)

                    drawCircle(
                        color = particleColors[i % particleColors.size].copy(alpha = particleAlpha),
                        radius = particleRadius,
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClickableLinkMessageText(
    body: String,
    isUser: Boolean,
    onLinkClick: (url: String) -> Unit,
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val defaultTextColor = Color.White
    val linkColor = if (isUser) Color(0xFF80D8FF) else Color(0xFF38BDF8)

    val urlRegex = remember {
        Regex("(https?://[^\\s]+|www\\.[^\\s]+|[a-zA-Z0-9.-]+\\.(?:com|net|org|com\\.tr|gov\\.tr|edu\\.tr|info|biz|io|ai|app|me|co|link|gl|page|tr)(?:/[^\\s]*)?)", RegexOption.IGNORE_CASE)
    }

    val hasUrl = remember(body) { urlRegex.containsMatchIn(body) }

    val annotatedString = remember(body, isUser, linkColor, hasUrl) {
        if (!hasUrl) return@remember null
        buildAnnotatedString {
            var lastIndex = 0
            val matches = urlRegex.findAll(body)

            for (match in matches) {
                val start = match.range.first
                val end = match.range.last + 1

                if (start > lastIndex) {
                    append(body.substring(lastIndex, start))
                }

                val rawUrl = match.value
                val fullUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) "https://$rawUrl" else rawUrl

                pushStringAnnotation(tag = "URL", annotation = fullUrl)
                pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    )
                )
                append(rawUrl)
                pop()
                pop()

                lastIndex = end
            }

            if (lastIndex < body.length) {
                append(body.substring(lastIndex))
            }
        }
    }

    if (annotatedString == null) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge.copy(color = defaultTextColor),
            modifier = Modifier.pointerInput(body) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTap()
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            }
        )
    } else {
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge.copy(color = defaultTextColor),
            onTextLayout = { layoutResult = it },
            modifier = Modifier.pointerInput(body) {
                detectTapGestures(
                    onTap = { pos ->
                        layoutResult?.let { layout ->
                            val offset = layout.getOffsetForPosition(pos)
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    onLinkClick(annotation.item)
                                }
                        }
                    },
                    onDoubleTap = {
                        onDoubleTap()
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    MphoneTheme {
        ChatScreen(
            thread = MockData.sampleSmsThreads.first(),
            onBackClick = {},
            onCallClick = {},
            onSendMessage = { _, _ -> SendStatus.Success }
        )
    }
}
