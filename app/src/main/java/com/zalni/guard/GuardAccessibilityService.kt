package com.zalni.guard

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.R
import com.example.ZalniGuardApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Phase 3 & 4 Accessibility Service for ZALNI Guard.
 * Monitors screen text for blocked keywords and guards device settings / launchers
 * against unauthorized force-stop, uninstall, or clearing app data.
 */
class GuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null
    private var settingsGuardJob: Job? = null

    @Volatile
    private var isProtectionEnabled: Boolean = false

    @Volatile
    private var pauseUntilMs: Long = 0L

    @Volatile
    private var cachedWords: Set<String> = emptySet()

    @Volatile
    private var cachedMatcher: KeywordMatcher? = null

    @Volatile
    private var lastBlockedTimestampMs: Long = 0L

    @Volatile
    private var lastSettingsGuardTimestampMs: Long = 0L

    @Volatile
    private var cachedLauncherPackages: Set<String> = emptySet()
    private var lastLauncherCheckTimeMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val SETTINGS_GUARD_DEBOUNCE_MS = 400L
        private const val SETTINGS_GUARD_COOLDOWN_MS = 2000L
        private const val MAX_TRAVERSAL_NODES = 1000
        private const val MAX_TRAVERSAL_DEPTH = 30
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Cache repository flows into memory for real-time background access
        serviceScope.launch {
            val repository = ZalniGuardApplication.instance.blockListRepository

            launch {
                repository.isProtectionEnabledFlow.collect { enabled ->
                    isProtectionEnabled = enabled
                }
            }

            launch {
                repository.pauseUntilFlow.collect { timestamp ->
                    pauseUntilMs = timestamp
                }
            }

            launch {
                repository.blockedWordsFlow.collect { words ->
                    cachedWords = words
                    cachedMatcher = KeywordMatcher(words)
                }
            }
        }
    }

    /**
     * Resolves and caches home launcher packages, refreshing periodically.
     */
    private fun getLauncherPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (cachedLauncherPackages.isEmpty() || now - lastLauncherCheckTimeMs > 60_000L) {
            cachedLauncherPackages = SettingsGuardConstants.getLauncherPackages(applicationContext)
            lastLauncherCheckTimeMs = now
        }
        return cachedLauncherPackages
    }

    /**
     * Checks if a package is a watched settings package, package installer, or home launcher.
     */
    private fun isWatchedPackage(packageName: String): Boolean {
        if (SettingsGuardConstants.ALL_WATCHED_PACKAGES.contains(packageName)) return true
        if (getLauncherPackages().contains(packageName)) return true
        return false
    }

    /**
     * Lazily reconstructs the KeywordMatcher if it was dropped during a low memory trim.
     */
    private fun getOrRebuildMatcher(): KeywordMatcher? {
        var matcher = cachedMatcher
        if (matcher == null && cachedWords.isNotEmpty()) {
            matcher = KeywordMatcher(cachedWords)
            cachedMatcher = matcher
        }
        return matcher
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Check settings and uninstall guard
        handleSettingsGuard(event)

        // 2. Keyword blocking engine checks
        if (!isProtectionEnabled) return

        // Ignore events from ZALNI Guard itself to allow password & blocklist management
        val eventPackage = event.packageName?.toString()
        if (eventPackage != null && (eventPackage == packageName || eventPackage == "com.zalni.guard")) {
            return
        }

        // Check 1.5-second cooldown to avoid continuous trigger loops
        val now = System.currentTimeMillis()
        if (now - lastBlockedTimestampMs < BLOCK_COOLDOWN_MS) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Check typed text directly
                val textBuilder = StringBuilder()
                for (item in event.text) {
                    textBuilder.append(item).append(" ")
                }
                val text = textBuilder.toString()
                if (text.isNotBlank() && getOrRebuildMatcher()?.matches(text) == true) {
                    triggerHomeRedirect()
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Debounce window events by 300 ms before traversing active window
                scanJob?.cancel()
                scanJob = serviceScope.launch {
                    delay(DEBOUNCE_DELAY_MS)
                    scanActiveWindow()
                }
            }
        }
    }

    /**
     * Handles Phase 4 Settings & Anti-Uninstall Guard.
     * Intercepts attempts to Force Stop, Uninstall, or Clear Storage in system Settings,
     * Package Installers, and Home Launchers.
     */
    private fun handleSettingsGuard(event: AccessibilityEvent) {
        // Run only when protection is ON
        if (!isProtectionEnabled) return

        // Check if guard is paused
        val now = System.currentTimeMillis()
        if (now < pauseUntilMs) return

        // Check 2-second cooldown
        if (now - lastSettingsGuardTimestampMs < SETTINGS_GUARD_COOLDOWN_MS) return

        val eventPackage = event.packageName?.toString() ?: return
        if (!isWatchedPackage(eventPackage)) return

        // 400 ms debounce before traversing the window
        settingsGuardJob?.cancel()
        settingsGuardJob = serviceScope.launch {
            delay(SETTINGS_GUARD_DEBOUNCE_MS)
            scanSettingsGuard(eventPackage)
        }
    }

    /**
     * Traverses the active window of a watched package to detect anti-tamper triggers.
     */
    private fun scanSettingsGuard(eventPackage: String) {
        val now = System.currentTimeMillis()
        if (!isProtectionEnabled || now < pauseUntilMs) return
        if (now - lastSettingsGuardTimestampMs < SETTINGS_GUARD_COOLDOWN_MS) return

        val rootNode: AccessibilityNodeInfo = try {
            rootInActiveWindow ?: return
        } catch (_: Exception) {
            return
        }

        val rootPackage = rootNode.packageName?.toString() ?: eventPackage
        if (!isWatchedPackage(rootPackage)) {
            safeRecycle(rootNode)
            return
        }

        val isInstaller = SettingsGuardConstants.isInstallerPackage(rootPackage) ||
                SettingsGuardConstants.isInstallerPackage(eventPackage)
        val isLauncher = getLauncherPackages().contains(rootPackage) ||
                getLauncherPackages().contains(eventPackage)

        val appLabel = getString(R.string.app_name)
        val appLabelLower = appLabel.lowercase()
        val dangerKeywords = if (isLauncher) {
            SettingsGuardConstants.LAUNCHER_DANGER_KEYWORDS
        } else {
            SettingsGuardConstants.DANGER_KEYWORDS
        }

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(rootNode, 0))

        var nodesVisited = 0
        var foundAppLabel = false
        var foundDangerWord = false

        while (!queue.isEmpty() && nodesVisited < MAX_TRAVERSAL_NODES) {
            val pair = queue.poll() ?: break
            val currentNode = pair.first
            val depth = pair.second
            nodesVisited++

            try {
                val text = currentNode.text?.toString()?.lowercase()
                val desc = currentNode.contentDescription?.toString()?.lowercase()

                if (!foundAppLabel) {
                    if ((text != null && text.contains(appLabelLower)) ||
                        (desc != null && desc.contains(appLabelLower))
                    ) {
                        foundAppLabel = true
                    }
                }

                if (!isInstaller && !foundDangerWord) {
                    for (keyword in dangerKeywords) {
                        if ((text != null && text.contains(keyword)) ||
                            (desc != null && desc.contains(keyword))
                        ) {
                            foundDangerWord = true
                            break
                        }
                    }
                }

                // Check early exit conditions
                if (isInstaller && foundAppLabel) {
                    break
                }
                if (!isInstaller && foundAppLabel && foundDangerWord) {
                    break
                }

                // Enqueue children if within limits
                if (depth < MAX_TRAVERSAL_DEPTH) {
                    val childCount = currentNode.childCount
                    for (i in 0 until childCount) {
                        if (nodesVisited + queue.size >= MAX_TRAVERSAL_NODES) break
                        val child = currentNode.getChild(i)
                        if (child != null) {
                            queue.add(Pair(child, depth + 1))
                        }
                    }
                }
            } finally {
                safeRecycle(currentNode)
            }
        }

        // Recycle remaining unvisited nodes
        while (!queue.isEmpty()) {
            val pair = queue.poll() ?: break
            safeRecycle(pair.first)
        }

        val shouldTrigger = if (isInstaller) {
            foundAppLabel
        } else {
            foundAppLabel && foundDangerWord
        }

        if (shouldTrigger) {
            triggerSettingsGuardAction()
        }
    }

    /**
     * Executes the Settings Guard action:
     * Redirects to Home screen, displays "Protected by ZALNI Guard" Toast,
     * and activates 2-second cooldown. Never logs or stores screen content.
     */
    private fun triggerSettingsGuardAction() {
        lastSettingsGuardTimestampMs = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.post {
            Toast.makeText(applicationContext, "Protected by ZALNI Guard", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Iteratively scans the root in active window with depth and node count capping for keyword blocking.
     */
    private fun scanActiveWindow() {
        val now = System.currentTimeMillis()
        if (now - lastBlockedTimestampMs < BLOCK_COOLDOWN_MS) return
        if (!isProtectionEnabled) return
        val matcher = getOrRebuildMatcher() ?: return

        val rootNode: AccessibilityNodeInfo = try {
            rootInActiveWindow ?: return
        } catch (_: Exception) {
            return
        }

        // Avoid self-blocking loops: do not scan or block when the user is inside ZALNI Guard
        val rootPackage = rootNode.packageName?.toString()
        if (rootPackage != null && (rootPackage == packageName || rootPackage == "com.zalni.guard")) {
            safeRecycle(rootNode)
            return
        }

        // Iterative BFS traversal (Node, Depth)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(rootNode, 0))

        var nodesVisited = 0
        var matched = false

        while (!queue.isEmpty() && nodesVisited < MAX_TRAVERSAL_NODES) {
            val pair = queue.poll() ?: break
            val currentNode = pair.first
            val depth = pair.second
            nodesVisited++

            try {
                // Inspect text and content description without logging or storing
                val text = currentNode.text
                if (!text.isNullOrEmpty() && matcher.matches(text)) {
                    matched = true
                }

                if (!matched) {
                    val desc = currentNode.contentDescription
                    if (!desc.isNullOrEmpty() && matcher.matches(desc)) {
                        matched = true
                    }
                }

                // Enqueue children if within depth and node limits
                if (!matched && depth < MAX_TRAVERSAL_DEPTH) {
                    val childCount = currentNode.childCount
                    for (i in 0 until childCount) {
                        if (nodesVisited + queue.size >= MAX_TRAVERSAL_NODES) break
                        val child = currentNode.getChild(i)
                        if (child != null) {
                            queue.add(Pair(child, depth + 1))
                        }
                    }
                }
            } finally {
                safeRecycle(currentNode)
            }

            if (matched) break
        }

        // Clean up any remaining nodes in the queue if traversal ended early
        while (!queue.isEmpty()) {
            val pair = queue.poll() ?: break
            safeRecycle(pair.first)
        }

        if (matched) {
            triggerHomeRedirect()
        }
    }

    /**
     * Safely recycles an AccessibilityNodeInfo on Android versions prior to API 34.
     */
    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        if (node != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                node.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * Executes the blocking action: redirects to Home screen, displays a brief Toast,
     * and sets the 1.5s cooldown timer. Never logs or stores screen content.
     */
    private fun triggerHomeRedirect() {
        lastBlockedTimestampMs = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.post {
            Toast.makeText(applicationContext, "Blocked", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_BACKGROUND) {
            // Drop in-memory matcher to release regex cache under low memory
            cachedMatcher = null
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        cachedMatcher = null
    }

    override fun onInterrupt() {
        scanJob?.cancel()
        settingsGuardJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isProtectionEnabled) {
            try {
                WatchdogNotifier.postWatchdogNotification(applicationContext)
            } catch (_: Exception) {}
        }
        serviceScope.cancel()
    }
}
