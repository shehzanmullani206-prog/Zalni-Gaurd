package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.BlockListRepository
import com.example.ui.screens.EnterPasswordScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PhoneSetupGuideScreen
import com.example.ui.screens.SetPasswordScreen
import com.example.ui.theme.ZalniGuardTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var blockListRepository: BlockListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security: Set FLAG_SECURE to prevent screenshots and hide previews in recent apps
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        passwordManager = PasswordManager(this)
        blockListRepository = (application as ZalniGuardApplication).blockListRepository

        // Start foreground service and watchdog if protection is already enabled
        lifecycleScope.launch {
            if (blockListRepository.isProtectionEnabledFlow.first()) {
                com.zalni.guard.ServiceController.startService(this@MainActivity)
            }
        }

        enableEdgeToEdge()

        setContent {
            ZalniGuardTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ZalniGuardApp(
                        passwordManager = passwordManager,
                        blockListRepository = blockListRepository,
                        onExitApp = { finish() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private enum class ScreenState {
    SET_PASSWORD,
    ENTER_PASSWORD,
    HOME,
    SETUP_GUIDE
}

@Composable
fun ZalniGuardApp(
    passwordManager: PasswordManager,
    blockListRepository: BlockListRepository,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Every cold start defaults to isUnlocked = false
    var isUnlocked by remember { mutableStateOf(false) }
    var hasPassword by remember { mutableStateOf(passwordManager.hasPassword()) }
    var isShowingSetupGuide by remember { mutableStateOf(false) }

    val hasSeenSetupGuide by blockListRepository.hasSeenSetupGuideFlow.collectAsStateWithLifecycle(initialValue = true)

    // Automatically trigger setup guide once on first unlock
    LaunchedEffect(isUnlocked) {
        if (isUnlocked && !hasSeenSetupGuide) {
            isShowingSetupGuide = true
        }
    }

    val currentScreen = when {
        !hasPassword -> ScreenState.SET_PASSWORD
        !isUnlocked -> ScreenState.ENTER_PASSWORD
        isShowingSetupGuide -> ScreenState.SETUP_GUIDE
        else -> ScreenState.HOME
    }

    when (currentScreen) {
        ScreenState.SET_PASSWORD -> {
            BackHandler {
                onExitApp()
            }
            SetPasswordScreen(
                passwordManager = passwordManager,
                onPasswordSet = {
                    hasPassword = true
                    isUnlocked = true
                },
                modifier = modifier
            )
        }

        ScreenState.ENTER_PASSWORD -> {
            BackHandler {
                onExitApp()
            }
            EnterPasswordScreen(
                passwordManager = passwordManager,
                onUnlocked = {
                    isUnlocked = true
                },
                modifier = modifier
            )
        }

        ScreenState.SETUP_GUIDE -> {
            BackHandler {
                isShowingSetupGuide = false
            }
            PhoneSetupGuideScreen(
                blockListRepository = blockListRepository,
                onBack = {
                    isShowingSetupGuide = false
                },
                modifier = modifier
            )
        }

        ScreenState.HOME -> {
            BackHandler {
                // Pressing back on home screen locks the app and asks for password
                isUnlocked = false
            }
            HomeScreen(
                passwordManager = passwordManager,
                blockListRepository = blockListRepository,
                onLockApp = {
                    isUnlocked = false
                },
                onOpenSetupGuide = {
                    isShowingSetupGuide = true
                },
                modifier = modifier
            )
        }
    }
}
