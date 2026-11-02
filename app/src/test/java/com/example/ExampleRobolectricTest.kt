package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BlockListRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var passwordManager: PasswordManager
    private lateinit var blockListRepository: BlockListRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        passwordManager = PasswordManager(context)
        passwordManager.clearPassword()
        blockListRepository = BlockListRepository(context)
    }

    @Test
    fun testAppName() {
        val appName = context.getString(R.string.app_name)
        assertEquals("ZALNI Guard", appName)
    }

    @Test
    fun testPasswordCreationAndVerification() {
        assertFalse(passwordManager.hasPassword())

        // Save password
        passwordManager.savePassword("SuperSecret123")
        assertTrue(passwordManager.hasPassword())

        // Correct password
        assertTrue(passwordManager.verifyPassword("SuperSecret123"))

        // Wrong password
        assertFalse(passwordManager.verifyPassword("WrongPassword"))
    }

    @Test
    fun testLockoutAfterFiveFailedAttempts() {
        passwordManager.savePassword("correct_pass")

        assertEquals(0, passwordManager.getRemainingLockoutSeconds())

        // 4 failed attempts
        for (i in 1..4) {
            val (attempts, lockedSec) = passwordManager.recordFailedAttempt()
            assertEquals(i, attempts)
            assertEquals(0, lockedSec)
        }

        // 5th failed attempt should trigger 30s lockout
        val (attempts5, lockedSec5) = passwordManager.recordFailedAttempt()
        assertEquals(5, attempts5)
        assertEquals(30, lockedSec5)
        assertTrue(passwordManager.getRemainingLockoutSeconds() > 0)
    }

    @Test
    fun testBlockListRepositoryAddAndRemove() = runBlocking {
        // Toggle protection
        blockListRepository.setProtectionEnabled(true)
        assertTrue(blockListRepository.isProtectionEnabledFlow.first())

        blockListRepository.setProtectionEnabled(false)
        assertFalse(blockListRepository.isProtectionEnabledFlow.first())

        // Add words
        blockListRepository.addWord("gambling")
        blockListRepository.addWord("violence")

        val words = blockListRepository.blockedWordsFlow.first()
        assertTrue(words.contains("gambling"))
        assertTrue(words.contains("violence"))

        // Remove word
        blockListRepository.removeWord("gambling")
        val updatedWords = blockListRepository.blockedWordsFlow.first()
        assertFalse(updatedWords.contains("gambling"))
        assertTrue(updatedWords.contains("violence"))
    }

    @Test
    fun testBlockListRepositoryPauseUntil() = runBlocking {
        assertEquals(0L, blockListRepository.pauseUntilFlow.first())

        val futureTimestamp = System.currentTimeMillis() + 300_000L
        blockListRepository.setPauseUntil(futureTimestamp)
        assertEquals(futureTimestamp, blockListRepository.pauseUntilFlow.first())

        blockListRepository.setPauseUntil(0L)
        assertEquals(0L, blockListRepository.pauseUntilFlow.first())
    }

    @Test
    fun testSettingsGuardConstantsClassification() {
        assertTrue(com.zalni.guard.SettingsGuardConstants.isSettingsPackage("com.android.settings"))
        assertTrue(com.zalni.guard.SettingsGuardConstants.isInstallerPackage("com.android.packageinstaller"))
        assertTrue(com.zalni.guard.SettingsGuardConstants.isInstallerPackage("com.google.android.packageinstaller"))
        assertFalse(com.zalni.guard.SettingsGuardConstants.isInstallerPackage("com.android.settings"))

        val dangerWords = com.zalni.guard.SettingsGuardConstants.DANGER_KEYWORDS
        assertTrue(dangerWords.contains("force stop"))
        assertTrue(dangerWords.contains("uninstall"))
        assertTrue(dangerWords.contains("clear data"))
        assertTrue(dangerWords.contains("फ़ोर्स स्टॉप"))
        assertTrue(dangerWords.contains("अनइंस्टॉल"))

        val launcherDanger = com.zalni.guard.SettingsGuardConstants.LAUNCHER_DANGER_KEYWORDS
        assertTrue(launcherDanger.contains("uninstall"))
        assertTrue(launcherDanger.contains("अनइंस्टॉल"))
        assertFalse(launcherDanger.contains("force stop"))
    }
}
