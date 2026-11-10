package com.mphone.ui.contacts

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mphone.data.MockData
import com.mphone.model.Contact
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricTeal
import com.mphone.ui.theme.ElectricViolet
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    searchQuery: String,
    onContactClick: (Contact) -> Unit,
    onCallClick: (Contact) -> Unit,
    onSmsClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = {},
    onEditContact: (id: String, name: String, number: String, photoUri: String?, note: String) -> Unit = { _, _, _, _, _ -> },
    onDeleteContact: (id: String, number: String) -> Unit = { _, _ -> }
) {
    var contactToEdit by remember { mutableStateOf<Contact?>(null) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }
    var cardIndex by remember { mutableIntStateOf(0) }

    val filteredContacts = remember(contacts, searchQuery) {
        contacts.filter { contact ->
            searchQuery.isBlank() ||
                    contact.name.contains(searchQuery, ignoreCase = true) ||
                    contact.phoneNumber.contains(searchQuery, ignoreCase = true)
        }.sortedBy { it.name }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Full-Screen Contact 3D Flip Card View
            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No contacts found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val safeIndex = remember(cardIndex, filteredContacts.size) {
                    if (filteredContacts.isEmpty()) 0
                    else Math.floorMod(cardIndex, filteredContacts.size)
                }
                val currentContact = filteredContacts[safeIndex]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    ContactGameCard(
                        contact = currentContact,
                        onSwipeNext = { cardIndex++ },
                        onSwipePrev = { cardIndex-- },
                        onSwipeCall = { onCallClick(currentContact) },
                        onSwipeChat = { onSmsClick(currentContact) },
                        onContactClick = { onContactClick(currentContact) },
                        onLongClick = { contactToEdit = currentContact }
                    )
                }
            }
        }

        // Modern Floating Action Button: Add Contact Logo
        FloatingActionButton(
            onClick = onAddContactClick,
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
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "Add Contact",
                modifier = Modifier.size(28.dp)
            )
        }

        // Edit Contact Dialog
        val targetEdit = contactToEdit
        if (targetEdit != null) {
            EditContactDialog(
                contact = targetEdit,
                onDismiss = { contactToEdit = null },
                onSave = { name, number, photoUri, note ->
                    onEditContact(targetEdit.id, name, number, photoUri, note)
                    contactToEdit = null
                },
                onDelete = {
                    onDeleteContact(targetEdit.id, targetEdit.phoneNumber)
                    contactToEdit = null
                }
            )
        }

        // Delete Contact Confirmation Dialog
        val targetDelete = contactToDelete
        if (targetDelete != null) {
            AlertDialog(
                onDismissRequest = { contactToDelete = null },
                title = { Text("Delete Contact") },
                text = { Text("Are you sure you want to delete ${targetDelete.name} from phone memory and app?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteContact(targetDelete.id, targetDelete.phoneNumber)
                            contactToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { contactToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Modern Clean Full-Screen Contact Card with Real-time 3D Card Flip Animation & Back-Side App Logo
 */
@Composable
private fun ContactGameCard(
    contact: Contact,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onSwipeCall: () -> Unit,
    onSwipeChat: () -> Unit,
    onContactClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val flipRotation = remember { Animatable(0f) }
    var isFlipping by remember { mutableStateOf(false) }

    val maxThresholdPx = with(density) { 90.dp.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "CardGlow")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )

    // Holographic Metallic Border Brush
    val cardBorderBrush = Brush.linearGradient(
        colors = listOf(
            ElectricIndigo,
            ElectricViolet,
            ElectricTeal,
            ElectricIndigo
        )
    )

    // Calculate Real-time 3D Rotation Y
    val liveDragRotationY = if (!isFlipping) (dragX.value / maxThresholdPx) * 55f else 0f
    val totalRotationY = flipRotation.value + liveDragRotationY
    val normalizedRotation = Math.floorMod(totalRotationY.toInt(), 360)
    val isShowingBack = normalizedRotation in 90..270

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(contact.id) {
                detectTapGestures(
                    onTap = { if (!isFlipping) onContactClick() },
                    onLongPress = {
                        if (!isFlipping) {
                            Toast.makeText(context, "Editing ${contact.name}", Toast.LENGTH_SHORT).show()
                            onLongClick()
                        }
                    }
                )
            }
            .pointerInput(contact.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (!isFlipping) {
                            change.consume()
                            scope.launch {
                                dragX.snapTo(dragX.value + dragAmount.x)
                                dragY.snapTo(dragY.value + dragAmount.y)
                            }
                        }
                    },
                    onDragEnd = {
                        if (!isFlipping) {
                            val dx = dragX.value
                            val dy = dragY.value

                            if (abs(dx) > abs(dy)) {
                                if (dx < -maxThresholdPx) {
                                    // Swipe Left -> 3D Flip to Next Contact
                                    isFlipping = true
                                    scope.launch {
                                        flipRotation.animateTo(180f, tween(320, easing = FastOutSlowInEasing))
                                        onSwipeNext()
                                        flipRotation.animateTo(360f, tween(320, easing = FastOutSlowInEasing))
                                        flipRotation.snapTo(0f)
                                        dragX.snapTo(0f)
                                        dragY.snapTo(0f)
                                        isFlipping = false
                                    }
                                } else if (dx > maxThresholdPx) {
                                    // Swipe Right -> 3D Flip to Previous Contact
                                    isFlipping = true
                                    scope.launch {
                                        flipRotation.animateTo(-180f, tween(320, easing = FastOutSlowInEasing))
                                        onSwipePrev()
                                        flipRotation.animateTo(-360f, tween(320, easing = FastOutSlowInEasing))
                                        flipRotation.snapTo(0f)
                                        dragX.snapTo(0f)
                                        dragY.snapTo(0f)
                                        isFlipping = false
                                    }
                                } else {
                                    scope.launch {
                                        dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                        dragY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    }
                                }
                            } else {
                                if (dy > maxThresholdPx) {
                                    // Swipe Down -> Call Contact
                                    Toast.makeText(context, "Calling ${contact.name}...", Toast.LENGTH_SHORT).show()
                                    onSwipeCall()
                                } else if (dy < -maxThresholdPx) {
                                    // Swipe Up -> Chat with Contact
                                    onSwipeChat()
                                }

                                scope.launch {
                                    dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    dragY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragY.value.roundToInt()) }
                .graphicsLayer {
                    rotationY = totalRotationY
                    cameraDistance = 16f * density.density
                }
        ) {
            if (isShowingBack) {
                // Back Side of Card: Shows App Logo (Mirrored to display right-side-up)
                ContactCardBackSide(cardBorderBrush = cardBorderBrush)
            } else {
                // Front Side of Card: Full-Screen Contact Photo Background or Dark Glass Surface
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.88f),
                    border = BorderStroke(2.dp, cardBorderBrush),
                    shadowElevation = 20.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Full Card Background Profile Photo (if contact has a photo)
                        if (!contact.photoUri.isNullOrBlank()) {
                            val imageRequest = remember(contact.photoUri) {
                                ImageRequest.Builder(context)
                                    .data(contact.photoUri)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = contact.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Dark Scrim Overlay for Legibility
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.45f),
                                                Color(0xFF0F172A).copy(alpha = 0.82f),
                                                Color(0xFF020617).copy(alpha = 0.95f)
                                            )
                                        )
                                    )
                            )
                        }

                        // Foreground Center Avatar & Contact Info Column
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Center Photo / Avatar
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(185.dp)
                                        .scale(auraScale)
                                        .border(2.dp, cardBorderBrush, CircleShape)
                                )

                                if (!contact.photoUri.isNullOrBlank()) {
                                    val imageRequest = remember(contact.photoUri) {
                                        ImageRequest.Builder(context)
                                            .data(contact.photoUri)
                                            .crossfade(true)
                                            .build()
                                    }
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = contact.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(170.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    )
                                } else {
                                    ContactAvatar(
                                        name = contact.name,
                                        photoUri = contact.photoUri,
                                        size = 170.dp
                                    )
                                }
                            }

                            // Contact Name
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 30.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Phone Number
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0F172A).copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                            ) {
                                Text(
                                    text = contact.phoneNumber,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.LightGray.copy(alpha = 0.9f),
                                    fontSize = 17.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Back of Contact Card showing the App Logo
 */
@Composable
private fun ContactCardBackSide(
    cardBorderBrush: Brush
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.95f),
        border = BorderStroke(2.dp, cardBorderBrush),
        shadowElevation = 20.dp,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { rotationY = 180f } // Counter-rotate so back content renders right-side-up
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E1B4B),
                            Color(0xFF020617)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Ambient glowing halo behind app logo
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ElectricIndigo.copy(alpha = 0.35f),
                                ElectricViolet.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // MPhone App Logo Badge
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.5.dp, ElectricIndigo),
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(110.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF334155),
                                        Color(0xFF0F172A)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "pcm Logo",
                            tint = ElectricIndigo,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "MUSTAFA DOĞAN",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ContactsScreenPreview() {
    MphoneTheme {
        ContactsScreen(
            contacts = MockData.sampleContacts,
            searchQuery = "",
            onContactClick = {},
            onCallClick = {},
            onSmsClick = {},
            onAddContactClick = {}
        )
    }
}
