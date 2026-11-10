package com.mphone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mphone.ui.theme.ElectricIndigo
import com.mphone.ui.theme.GlassBorderBrush

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    PHONE("Phone", Icons.Filled.Call, Icons.Outlined.Call),
    CONTACTS("Contacts", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    MESSAGES("Messages", Icons.AutoMirrored.Filled.Message, Icons.AutoMirrored.Outlined.Message)
}

@Composable
fun AppBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    unreadSmsCount: Int = 0,
    missedCallCount: Int = 0
) {
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.88f),
        shadowElevation = 20.dp,
        border = BorderStroke(1.2.dp, GlassBorderBrush),
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(36.dp))
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == currentTab
                val icon = if (isSelected) tab.selectedIcon else tab.unselectedIcon
                val badgeCount = when (tab) {
                    MainTab.MESSAGES -> unreadSmsCount
                    MainTab.PHONE -> missedCallCount
                    else -> 0
                }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    alwaysShowLabel = false,
                    label = null,
                    icon = {
                        if (badgeCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = Color.White
                                    ) {
                                        Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                    }
                                }
                            ) {
                                Icon(imageVector = icon, contentDescription = tab.title)
                            }
                        } else {
                            Icon(imageVector = icon, contentDescription = tab.title)
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = ElectricIndigo.copy(alpha = 0.35f),
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.LightGray.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}
