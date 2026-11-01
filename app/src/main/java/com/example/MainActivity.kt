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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.EnterPasswordScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SetPasswordScreen
import com.example.ui.theme.ZalniGuardTheme

class MainActivity : ComponentActivity() {

    private lateinit var passwordManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security: Set FLAG_SECURE to prevent screenshots and hide previews in recent apps
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        passwordManager = PasswordManager(this)

        enableEdgeToEdge()

        setContent {
            ZalniGuardTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ZalniGuardApp(
                        passwordManager = passwordManager,
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
    HOME
}

@Composable
fun ZalniGuardApp(
    passwordManager: PasswordManager,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Every cold start defaults to isUnlocked = false
    var isUnlocked by remember { mutableStateOf(false) }
    var hasPassword by remember { mutableStateOf(passwordManager.hasPassword()) }

    val currentScreen = when {
        !hasPassword -> ScreenState.SET_PASSWORD
        !isUnlocked -> ScreenState.ENTER_PASSWORD
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

        ScreenState.HOME -> {
            BackHandler {
                // Pressing back on home screen locks the app and asks for password
                isUnlocked = false
            }
            HomeScreen(
                passwordManager = passwordManager,
                onLockApp = {
                    isUnlocked = false
                },
                onResetPassword = {
                    passwordManager.clearPassword()
                    hasPassword = false
                    isUnlocked = false
                },
                modifier = modifier
            )
        }
    }
}
