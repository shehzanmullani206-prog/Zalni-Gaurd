package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GuardGreen

enum class HealthLevel(val label: String, val color: Color) {
    OPTIMAL("Optimal Protection", GuardGreen),
    ATTENTION("Action Recommended", Color(0xFFF59E0B)), // Orange
    CRITICAL("Protection Inactive", Color(0xFFEF4444))   // Red
}

/**
 * Protection Health Card at the top of the Home Screen displaying
 * live green/orange/red status of system permissions, device admin, and setup guide items.
 */
@Composable
fun ProtectionHealthCard(
    isAccessibilityEnabled: Boolean,
    isNotificationGranted: Boolean,
    isBatteryExempt: Boolean,
    isDeviceAdminActive: Boolean,
    isDeviceOwnerActive: Boolean,
    setupStepsCompletedCount: Int,
    setupStepsTotalCount: Int,
    onFixAccessibility: () -> Unit,
    onFixNotification: () -> Unit,
    onFixBattery: () -> Unit,
    onFixDeviceAdmin: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    onReleaseDeviceOwner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val healthLevel = when {
        !isAccessibilityEnabled -> HealthLevel.CRITICAL
        !isBatteryExempt || !isDeviceAdminActive || setupStepsCompletedCount < setupStepsTotalCount -> HealthLevel.ATTENTION
        else -> HealthLevel.OPTIMAL
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("protection_health_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Overall Health Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = healthLevel.color.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (healthLevel) {
                                HealthLevel.OPTIMAL -> Icons.Default.HealthAndSafety
                                HealthLevel.ATTENTION -> Icons.Default.Warning
                                HealthLevel.CRITICAL -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = healthLevel.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Protection Health",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = healthLevel.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = healthLevel.color
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Accessibility Service Item
            HealthItemRow(
                title = "Accessibility Engine",
                subtitle = if (isAccessibilityEnabled) "Active & monitoring screen content" else "Required for keyword detection - Tap to fix",
                statusColor = if (isAccessibilityEnabled) GuardGreen else Color(0xFFEF4444),
                isPassing = isAccessibilityEnabled,
                onClick = onFixAccessibility,
                testTag = "health_item_accessibility"
            )

            // 2. Battery Exemption Item
            HealthItemRow(
                title = "Battery Unrestricted",
                subtitle = if (isBatteryExempt) "Exempt from system task killer" else "Standard mode may pause service - Tap to exempt",
                statusColor = if (isBatteryExempt) GuardGreen else Color(0xFFF59E0B),
                isPassing = isBatteryExempt,
                onClick = onFixBattery,
                testTag = "health_item_battery"
            )

            // 3. Notification Permission Item
            HealthItemRow(
                title = "Security Notifications",
                subtitle = if (isNotificationGranted) "Alerts & ongoing status enabled" else "Permission missing - Tap to allow",
                statusColor = if (isNotificationGranted) GuardGreen else Color(0xFFF59E0B),
                isPassing = isNotificationGranted,
                onClick = onFixNotification,
                testTag = "health_item_notifications"
            )

            // 4. Device Admin Item
            HealthItemRow(
                title = "Anti-Uninstall Admin",
                subtitle = if (isDeviceAdminActive) "Uninstall protection locked" else "Standard uninstall active - Tap to enable Admin",
                statusColor = if (isDeviceAdminActive) GuardGreen else Color(0xFFF59E0B),
                isPassing = isDeviceAdminActive,
                onClick = onFixDeviceAdmin,
                testTag = "health_item_device_admin"
            )

            // 5. Device Owner Item (Optional Enterprise MDM)
            HealthItemRow(
                title = "Device Owner (MDM)",
                subtitle = if (isDeviceOwnerActive) "Provisioned (Maximum persistence - Tap to release)" else "Not provisioned (Optional via ADB)",
                statusColor = if (isDeviceOwnerActive) GuardGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                isPassing = isDeviceOwnerActive,
                onClick = onReleaseDeviceOwner,
                isClickable = isDeviceOwnerActive,
                testTag = "health_item_device_owner"
            )

            // 6. Setup Guide Steps Item
            val isSetupComplete = setupStepsCompletedCount >= setupStepsTotalCount && setupStepsTotalCount > 0
            HealthItemRow(
                title = "Phone Setup Guide",
                subtitle = if (isSetupComplete) "All OEM autostart & recents steps done" else "$setupStepsCompletedCount of $setupStepsTotalCount steps done - Tap to view",
                statusColor = if (isSetupComplete) GuardGreen else Color(0xFFF59E0B),
                isPassing = isSetupComplete,
                onClick = onOpenSetupGuide,
                testTag = "health_item_setup_guide"
            )
        }
    }
}

@Composable
private fun HealthItemRow(
    title: String,
    subtitle: String,
    statusColor: Color,
    isPassing: Boolean,
    onClick: () -> Unit,
    isClickable: Boolean = true,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(statusColor.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPassing) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        if (isClickable) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Fix",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
