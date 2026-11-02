# Preserve Core App Components
-keep class com.example.ZalniGuardApplication { *; }
-keep class com.zalni.guard.GuardAccessibilityService { *; }
-keep class com.zalni.guard.GuardForegroundService { *; }
-keep class com.zalni.guard.BootReceiver { *; }
-keep class com.zalni.guard.GuardDeviceAdminReceiver { *; }
-keep class com.zalni.guard.WatchdogWorker { *; }
-keep class com.zalni.guard.KeywordMatcher { *; }

# WorkManager Workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# DataStore & Preferences
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
