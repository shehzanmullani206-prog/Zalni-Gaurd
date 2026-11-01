package com.example

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Handles PBKDF2WithHmacSHA256 password hashing, verification with constant-time comparison,
 * and persistent lockout tracking across cold restarts.
 */
class PasswordManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "zalni_guard_prefs"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_ms"

        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16

        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L
    }

    /**
     * Checks if a password has already been created.
     */
    fun hasPassword(): Boolean {
        val hash = prefs.getString(KEY_PASSWORD_HASH, null)
        val salt = prefs.getString(KEY_PASSWORD_SALT, null)
        return !hash.isNullOrEmpty() && !salt.isNullOrEmpty()
    }

    /**
     * Hashes the plaintext password with PBKDF2WithHmacSHA256 using a cryptographically
     * secure 16-byte random salt and 120,000 iterations. Stores hash and salt in Base64.
     */
    fun savePassword(password: String) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)

        val hash = hashPassword(password.toCharArray(), salt)

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_PASSWORD_SALT, saltBase64)
            .putString(KEY_PASSWORD_HASH, hashBase64)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
    }

    /**
     * Verifies the entered password against the stored PBKDF2 hash using constant-time comparison.
     */
    fun verifyPassword(password: String): Boolean {
        val storedHashBase64 = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PASSWORD_SALT, null) ?: return false

        val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)

        val computedHash = hashPassword(password.toCharArray(), salt)

        // Constant-time byte array comparison prevents timing attack analysis
        val matches = MessageDigest.isEqual(expectedHash, computedHash)

        if (matches) {
            resetFailedAttempts()
        }
        return matches
    }

    /**
     * Returns the remaining lockout seconds, or 0 if not locked.
     */
    fun getRemainingLockoutSeconds(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            val remainingMs = lockoutUntil - now
            return ((remainingMs + 999) / 1000).toInt()
        }
        return 0
    }

    /**
     * Records a failed password attempt.
     * When reaching 5 failed attempts, enforces a 30-second lockout.
     * Returns Pair(failedAttemptsCount, remainingLockoutSeconds).
     */
    fun recordFailedAttempt(): Pair<Int, Int> {
        val currentAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit()

        if (currentAttempts >= MAX_ATTEMPTS) {
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            editor.putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
            editor.putInt(KEY_FAILED_ATTEMPTS, 0)
            editor.apply()
            return Pair(MAX_ATTEMPTS, (LOCKOUT_DURATION_MS / 1000).toInt())
        } else {
            editor.putInt(KEY_FAILED_ATTEMPTS, currentAttempts)
            editor.apply()
            return Pair(currentAttempts, 0)
        }
    }

    /**
     * Returns the current failed attempts count before lockout.
     */
    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    /**
     * Resets failed attempts and clears any lockout.
     */
    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
    }

    /**
     * Clears all stored password and lockout data.
     */
    fun clearPassword() {
        prefs.edit().clear().apply()
    }

    private fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}
