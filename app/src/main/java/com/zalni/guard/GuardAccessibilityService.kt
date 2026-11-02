package com.zalni.guard

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
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
 * Phase 3 & 5 Accessibility Service for ZALNI Guard.
 * Monitors screen text and active window hierarchy to detect blocked keywords,
 * redirecting to the Android Home screen when triggered.
 * Implements lazy keyword matcher rebuilding and memory trimming for 4 GB RAM devices.
 */
class GuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null

    @Volatile
    private var isProtectionEnabled: Boolean = false

    @Volatile
    private var cachedWords: Set<String> = emptySet()

    @Volatile
    private var cachedMatcher: KeywordMatcher? = null

    @Volatile
    private var lastBlockedTimestampMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
        private const val BLOCK_COOLDOWN_MS = 1500L
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
                repository.blockedWordsFlow.collect { words ->
                    cachedWords = words
                    cachedMatcher = KeywordMatcher(words)
                }
            }
        }
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

        // Call Phase 4 settings guard hook
        handleSettingsGuard(event)

        // If protection is disabled, return immediately
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
     * Iteratively scans the root in active window with depth and node count capping.
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
                // Safely recycle node on API < 34 (on API 34+ recycle is a deprecated no-op)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        currentNode.recycle()
                    } catch (_: Exception) {}
                }
            }

            if (matched) break
        }

        // Clean up any remaining nodes in the queue if traversal ended early
        while (!queue.isEmpty()) {
            val pair = queue.poll() ?: break
            val node = pair.first
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    node.recycle()
                } catch (_: Exception) {}
            }
        }

        if (matched) {
            triggerHomeRedirect()
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

    /**
     * Hook for Device Admin and system Settings guard.
     */
    private fun handleSettingsGuard(event: AccessibilityEvent) {
        // TODO PHASE 4: Handle settings guard and prevent unauthorized tampering/uninstall
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
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
