package com.mphone.ui.sms

import android.content.Context
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.ElectricTeal

data class SimSlotInfo(
    val slotIndex: Int, // 0 for SIM 1, 1 for SIM 2
    val subscriptionId: Int,
    val carrierName: String,
    val number: String
)

@Composable
fun SimSelectionDialog(
    simSlots: List<SimSlotInfo>,
    onDismiss: () -> Unit,
    onSimSelected: (subId: Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = ElectricIndigo,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Select SIM Card",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose which SIM card to use for sending this message:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                val defaultSlots = if (simSlots.isEmpty()) {
                    listOf(
                        SimSlotInfo(0, -1, "SIM 1", "Line 1"),
                        SimSlotInfo(1, -1, "SIM 2", "Line 2")
                    )
                } else simSlots

                defaultSlots.forEach { slot ->
                    val color = if (slot.slotIndex == 0) ElectricIndigo else ElectricTeal
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                onSimSelected(slot.subscriptionId)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SIM ${slot.slotIndex + 1} • ${slot.carrierName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (slot.number.isNotBlank()) {
                                    Text(
                                        text = slot.number,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Suppress("MissingPermission")
fun getActiveSimSlots(context: Context): List<SimSlotInfo> {
    return try {
        val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
        if (manager is SubscriptionManager) {
            val activeSubscriptions = manager.activeSubscriptionInfoList
            if (!activeSubscriptions.isNullOrEmpty()) {
                activeSubscriptions.mapIndexed { index, info ->
                    @Suppress("DEPRECATION")
                    val num = info.number ?: ""
                    SimSlotInfo(
                        slotIndex = info.simSlotIndex.coerceAtLeast(index),
                        subscriptionId = info.subscriptionId,
                        carrierName = info.displayName?.toString()?.ifBlank { "Carrier ${index + 1}" } ?: "SIM ${index + 1}",
                        number = num
                    )
                }
            } else {
                listOf(
                    SimSlotInfo(0, -1, "SIM 1", ""),
                    SimSlotInfo(1, -1, "SIM 2", "")
                )
            }
        } else {
            listOf(
                SimSlotInfo(0, -1, "SIM 1", ""),
                SimSlotInfo(1, -1, "SIM 2", "")
            )
        }
    } catch (_: Throwable) {
        listOf(
            SimSlotInfo(0, -1, "SIM 1", ""),
            SimSlotInfo(1, -1, "SIM 2", "")
        )
    }
}
