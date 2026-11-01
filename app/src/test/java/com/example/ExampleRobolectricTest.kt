package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        passwordManager = PasswordManager(context)
        passwordManager.clearPassword()
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
}
