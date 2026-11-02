package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.PasswordManager
import com.example.data.BlockListRepository
import com.example.ui.components.PasswordConfirmDialog
import com.example.ui.components.ProtectionHealthCard
import com.example.ui.theme.GuardGreen
import com.example.util.PermissionUtils
import com.zalni.guard.MakerSetupHelper
import com.zalni.guard.ServiceController
import kotlinx.coroutines.launch

/**
 * Phase 5 Home Screen with Protection Health Card at the top,
 * ON/OFF master switch, keyword management, setup guide shortcut, and permission checklist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    passwordManager: PasswordManager,
    blockListRepository: BlockListRepository,
    onLockApp: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Flow states from DataStore
    val isProtectionEnabled by blockListRepository.isProtectionEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val pauseUntil by blockListRepository.pauseUntilFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val blockedWords by blockListRepository.blockedWordsFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val completedSetupSteps by blockListRepository.setupGuideStepsFlow.collectAsStateWithLifecycle(initialValue = emptySet())

    val detectedMaker = remember { MakerSetupHelper.detectMaker() }
    val totalSetupSteps = remember(detectedMaker) { MakerSetupHelper.getStepsForMaker(context, detectedMaker).size }

    // Keyword input state
    var wordInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    // Dialog state for password verification
    var showTurnOffDialog by remember { mutableStateOf(false) }
    var showPauseGuardDialog by remember { mutableStateOf(false) }
    var showReleaseDeviceOwnerDialog by remember { mutableStateOf(false) }
    var wordToDelete by remember { mutableStateOf<String?>(null) }

    // Live permission states
    var isAccessibilityGranted by remember { mutableStateOf(false) }
    var isBatteryOptGranted by remember { mutableStateOf(false) }
    var isNotificationGranted by remember { mutableStateOf(false) }
    var isDeviceAdminActive by remember { mutableStateOf(false) }
    var isDeviceOwnerActive by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        isAccessibilityGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
        isBatteryOptGranted = PermissionUtils.isBatteryOptimizationIgnored(context)
        isNotificationGranted = PermissionUtils.isNotificationPermissionGranted(context)
        isDeviceAdminActive = PermissionUtils.isDeviceAdminActive(context)
        isDeviceOwnerActive = PermissionUtils.isDeviceOwnerActive(context)
        if (isDeviceOwnerActive) {
            PermissionUtils.applyDeviceOwnerRestrictions(context)
        }
    }

    // Refresh permission statuses in onResume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshPermissions()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Launcher for POST_NOTIFICATIONS on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    fun handleAddWord() {
        val sanitized = wordInput.trim().lowercase().replace(Regex("\\s+"), " ")

        when {
            sanitized.isEmpty() -> {
                inputError = "Word cannot be empty"
            }
            sanitized.length > 40 -> {
                inputError = "Word cannot exceed 40 characters"
            }
            blockedWords.contains(sanitized) -> {
                inputError = "Word is already in blocklist"
            }
            else -> {
                inputError = null
                coroutineScope.launch {
                    blockListRepository.addWord(sanitized)
                    wordInput = ""
                }
            }
        }
    }

    // Password Dialog: Turning OFF Protection
    if (showTurnOffDialog) {
        PasswordConfirmDialog(
            title = "Turn Off Protection",
            message = "Enter your master password to disable keyword monitoring.",
            passwordManager = passwordManager,
            onConfirmed = {
                showTurnOffDialog = false
                coroutineScope.launch {
                    blockListRepository.setProtectionEnabled(false)
                    blockListRepository.setPauseUntil(0L)
                    ServiceController.stopService(context)
                }
            },
            onDismiss = {
                showTurnOffDialog = false
            }
        )
    }

    // Password Dialog: Pausing Settings & Uninstall Guard for 5 minutes
    if (showPauseGuardDialog) {
        PasswordConfirmDialog(
            title = "Pause Settings Guard",
            message = "Enter your master password to pause settings and uninstall protection for 5 minutes.",
            passwordManager = passwordManager,
            onConfirmed = {
                showPauseGuardDialog = false
                coroutineScope.launch {
                    blockListRepository.setPauseUntil(System.currentTimeMillis() + 5 * 60 * 1000L)
                }
            },
            onDismiss = {
                showPauseGuardDialog = false
            }
        )
    }

    // Password Dialog: Releasing Device Owner
    if (showReleaseDeviceOwnerDialog) {
        PasswordConfirmDialog(
            title = "Release Device Owner",
            message = "Enter your master password to release Device Owner privileges and restore normal device administration.",
            passwordManager = passwordManager,
            onConfirmed = {
                showReleaseDeviceOwnerDialog = false
                PermissionUtils.releaseDeviceOwner(context)
                refreshPermissions()
            },
            onDismiss = {
                showReleaseDeviceOwnerDialog = false
            }
        )
    }

    // Password Dialog: Deleting a Blocked Word
    wordToDelete?.let { targetWord ->
        PasswordConfirmDialog(
            title = "Delete Blocked Keyword",
            message = "Enter your master password to unblock \"$targetWord\".",
            passwordManager = passwordManager,
            onConfirmed = {
                val toDelete = targetWord
                wordToDelete = null
                coroutineScope.launch {
                    blockListRepository.removeWord(toDelete)
                }
            },
            onDismiss = {
                wordToDelete = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ZALNI Guard",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenSetupGuide,
                    modifier = Modifier.testTag("action_open_setup_guide")
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = "Phone Setup Guide",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onLockApp,
                    modifier = Modifier.testTag("action_lock_app")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock App",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. PROTECTION HEALTH CARD (At top of Home Screen)
            item {
                ProtectionHealthCard(
                    isAccessibilityEnabled = isAccessibilityGranted,
                    isNotificationGranted = isNotificationGranted,
                    isBatteryExempt = isBatteryOptGranted,
                    isDeviceAdminActive = isDeviceAdminActive,
                    isDeviceOwnerActive = isDeviceOwnerActive,
                    setupStepsCompletedCount = completedSetupSteps.size,
                    setupStepsTotalCount = totalSetupSteps,
                    onFixAccessibility = { PermissionUtils.openAccessibilitySettings(context) },
                    onFixNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onFixBattery = { PermissionUtils.requestBatteryOptimizationExemption(context) },
                    onFixDeviceAdmin = { PermissionUtils.requestDeviceAdmin(context) },
                    onOpenSetupGuide = onOpenSetupGuide,
                    onReleaseDeviceOwner = { showReleaseDeviceOwnerDialog = true }
                )
            }

            // 2. PROTECTION STATUS CARD (with ON/OFF Switch and Settings Guard Pause)
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("protection_status_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            color = if (isProtectionEnabled) GuardGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isProtectionEnabled) Icons.Default.Security else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isProtectionEnabled) GuardGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = if (isProtectionEnabled) "Protection: ON" else "Protection: OFF",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isProtectionEnabled) GuardGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isProtectionEnabled) "Keyword monitoring active" else "Protection is currently disabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isProtectionEnabled,
                                onCheckedChange = { willEnable ->
                                    if (willEnable) {
                                        coroutineScope.launch {
                                            blockListRepository.setProtectionEnabled(true)
                                            ServiceController.startService(context)
                                        }
                                    } else {
                                        showTurnOffDialog = true
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.testTag("protection_switch")
                            )
                        }

                        // Settings Guard Status & 5-minute pause option (shown only when protection is ON)
                        if (isProtectionEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            )

                            val isGuardPaused = pauseUntil > System.currentTimeMillis()
                            val remainingMinutes = if (isGuardPaused) {
                                (((pauseUntil - System.currentTimeMillis()) / 1000L) + 59) / 60
                            } else 0L

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isGuardPaused) "Settings Guard Paused" else "Settings Guard Active",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (isGuardPaused) MaterialTheme.colorScheme.error else GuardGreen
                                    )
                                    Text(
                                        text = if (isGuardPaused) {
                                            "Resumes in ~$remainingMinutes min (anti-tamper paused)"
                                        } else {
                                            "Guards settings & prevents uninstall"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isGuardPaused) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                blockListRepository.setPauseUntil(0L)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("resume_guard_button")
                                    ) {
                                        Text("Resume")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { showPauseGuardDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("pause_guard_button")
                                    ) {
                                        Text("Pause guard for 5 minutes")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. ADD KEYWORD CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Add Keyword to Block",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = wordInput,
                                onValueChange = {
                                    wordInput = it
                                    if (inputError != null) inputError = null
                                },
                                placeholder = { Text("Enter word to block") },
                                singleLine = true,
                                isError = inputError != null,
                                supportingText = {
                                    if (inputError != null) {
                                        Text(
                                            text = inputError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { handleAddWord() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("block_word_input")
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = { handleAddWord() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("add_word_button")
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }
            }

            // 4. BLOCKED KEYWORDS LIST HEADER
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Blocked Keywords",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${blockedWords.size} word${if (blockedWords.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Empty state for blocked keywords
            if (blockedWords.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No keywords blocked yet. Add words above to safeguard this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            } else {
                items(
                    items = blockedWords.toList().sorted(),
                    key = { it }
                ) { word ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blocked_word_item_$word")
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = word,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { wordToDelete = word },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("delete_word_$word")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete $word",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
