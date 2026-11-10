package com.mphone.ui.contacts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mphone.data.MockData
import com.mphone.model.Contact
import com.mphone.ui.components.ContactAvatar
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricRose
import com.mphone.ui.theme.ElectricViolet
import com.mphone.ui.theme.GlassBorderBrush
import com.mphone.ui.theme.MissedRed
import com.mphone.ui.theme.MphoneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditContactDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (name: String, number: String, photoUri: String?, note: String) -> Unit,
    onDelete: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        EditContactScreen(
            contact = contact,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactScreen(
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (name: String, number: String, photoUri: String?, note: String) -> Unit,
    onDelete: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(contact.name) }
    var number by remember { mutableStateOf(contact.phoneNumber) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(contact.photoUri?.let { Uri.parse(it) }) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Parse multi-line notes
    var notesList by remember {
        mutableStateOf(
            if (contact.note.isNotBlank()) contact.note.split("\n").filter { it.isNotBlank() }.ifEmpty { listOf("") }
            else listOf("")
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    val currentCombinedNotes = remember(notesList) {
        notesList.filter { it.isNotBlank() }.joinToString("\n")
    }

    // Check if any field has actually been edited
    val hasChanges = remember(name, number, selectedPhotoUri, currentCombinedNotes, contact) {
        name.trim() != contact.name.trim() ||
                number.trim() != contact.phoneNumber.trim() ||
                (selectedPhotoUri?.toString() ?: "") != (contact.photoUri ?: "") ||
                currentCombinedNotes != contact.note.trim()
    }

    val isValid = name.trim().isNotBlank() && number.trim().isNotBlank()
    val canSave = isValid && hasChanges && !isSaving && !isDeleting

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Edit Contact",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            // Delete Contact Logo Icon Button with Deleting Progress
                            IconButton(
                                onClick = {
                                    if (!isDeleting && !isSaving) {
                                        isDeleting = true
                                        scope.launch {
                                            delay(600L)
                                            onDelete()
                                            isDeleting = false
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = !isDeleting && !isSaving,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MissedRed.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Contact",
                                    tint = MissedRed
                                )
                            }

                            // Save Changes Logo Icon Button with Saving Progress
                            IconButton(
                                onClick = {
                                    if (canSave) {
                                        isSaving = true
                                        scope.launch {
                                            delay(600L)
                                            onSave(name.trim(), number.trim(), selectedPhotoUri?.toString(), currentCombinedNotes)
                                            isSaving = false
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = canSave,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clip(CircleShape)
                                    .background(if (canSave) ElectricIndigo else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save Changes",
                                    tint = if (canSave) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Centered Profile Photo Picker Section
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ElectricIndigo.copy(alpha = 0.35f),
                                            ElectricViolet.copy(alpha = 0.10f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            ContactAvatar(
                                name = name.ifBlank { "Contact" },
                                photoUri = selectedPhotoUri?.toString(),
                                colorIndex = contact.avatarColorIndex,
                                size = 104.dp
                            )

                            // Camera Add/Change Photo Badge
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElectricIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Change Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Remove Profile Picture Icon Badge
                            if (selectedPhotoUri != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MissedRed)
                                        .clickable { selectedPhotoUri = null },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Profile Picture",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (selectedPhotoUri != null) {
                        TextButton(onClick = { selectedPhotoUri = null }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MissedRed, modifier = Modifier.size(16.dp))
                                Text("Remove Photo", color = MissedRed, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input Fields
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Dynamic Multi-Note Section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Notes (${notesList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = {
                                    notesList = notesList + ""
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Note",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        notesList.forEachIndexed { index, noteText ->
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { newText ->
                                    val updated = notesList.toMutableList()
                                    updated[index] = newText
                                    notesList = updated
                                },
                                label = { Text(if (notesList.size > 1) "Note #${index + 1}" else "Note (Optional)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Note,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (noteText.isNotBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.Save,
                                                contentDescription = "Note Saved",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(end = 6.dp)
                                                    .size(20.dp)
                                            )
                                        }
                                        if (notesList.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val updated = notesList.toMutableList()
                                                    updated.removeAt(index)
                                                    notesList = updated.ifEmpty { listOf("") }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove Note",
                                                    tint = MissedRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                singleLine = false,
                                maxLines = 3,
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Modern In-Tree Liquid Glass Progress Overlay
            if (isSaving || isDeleting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.96f),
                        shadowElevation = 24.dp,
                        border = BorderStroke(1.2.dp, GlassBorderBrush),
                        modifier = Modifier
                            .width(280.dp)
                            .clip(RoundedCornerShape(28.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        if (isDeleting) MissedRed.copy(alpha = 0.40f) else ElectricIndigo.copy(alpha = 0.40f),
                                                        if (isDeleting) ElectricRose.copy(alpha = 0.10f) else ElectricViolet.copy(alpha = 0.10f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                    CircularProgressIndicator(
                                        color = if (isDeleting) MissedRed else ElectricIndigo,
                                        strokeWidth = 3.5.dp,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = if (isDeleting) "Deleting Contact..." else "Updating Contact...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Please wait a moment...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditContactScreenPreview() {
    MphoneTheme {
        EditContactScreen(
            contact = MockData.sampleContacts.first(),
            onDismiss = {},
            onSave = { _, _, _, _ -> }
        )
    }
}
