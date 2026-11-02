# ZALNI Guard — Hardware Compatibility & Verification Guide

This checklist is designed for validating **ZALNI Guard** across the target 4 GB RAM devices:
1. **Tecno Spark Go 1** (HiOS Go / Android 14 Go)
2. **Redmi 10A Sport** (MIUI 12.5 / 13)
3. **Oppo Reno2 Z** (ColorOS 11)
4. **Tecno Pop 8** (HiOS 13 Go)
5. **Redmi A5** (HyperOS / Android 14)

---

## 1. First Launch & Password Gate (Phase 1)
- [ ] **First Launch Prompt**: On fresh install, app immediately displays "Set Master Password" with two password fields.
- [ ] **Validation Rules**:
  - [ ] Entering < 4 characters shows inline error: "Minimum 4 characters required".
  - [ ] Password mismatch shows inline error: "Passwords do not match".
  - [ ] Empty submission shows inline error: "Password cannot be empty".
- [ ] **Show/Hide Toggle**: Tapping the eye icon toggles plaintext vs masked bullets.
- [ ] **PBKDF2 Storage**: Password is saved with 16-byte random salt and 120,000 PBKDF2-HMAC-SHA256 iterations. No plaintext stored.
- [ ] **FLAG_SECURE**: Screenshots are blocked (OS shows "Can't take screenshot due to security policy") and the app preview is hidden in the Recents carousel.
- [ ] **Cold Start Gate**: Force-quitting the app and reopening always prompts for the password.
- [ ] **Lockout Mechanism**: Entering 5 consecutive incorrect passwords locks input for 30 seconds with a visible countdown timer.

---

## 2. Phone Setup Guide & Task Killer Resilience (Phase 5)
- [ ] **Automatic First-Unlock Prompt**: The "Phone Setup Guide" automatically opens immediately after first password initialization.
- [ ] **OEM Detection**:
  - [ ] On **Tecno Spark Go 1 & Tecno Pop 8**: Detected as `Tecno / Infinix / itel (Transsion)`.
  - [ ] On **Redmi 10A Sport & Redmi A5**: Detected as `Xiaomi / Redmi / Poco`.
  - [ ] On **Oppo Reno2 Z**: Detected as `Oppo / Realme / OnePlus`.
- [ ] **Auto-Start Setting**: Tapping "Open Setting" launches the maker's autostart manager (or App Info fallback). Checkbox persists as "Done".
- [ ] **Battery "No Restrictions"**: Tapping "Open Setting" deep-links to Battery Optimization settings. Checkbox persists as "Done".
- [ ] **Lock in Recents**: User pulls down or long-presses ZALNI Guard in the app switcher to lock it. Checkbox persists as "Done".
- [ ] **Restricted Settings (Android 13+)**: On Android 13/14, displays step to tap 3-dots in App Info to "Allow restricted settings" if accessibility toggle was grayed out.

---

## 3. Protection Health Card (Phase 5)
- [ ] **Health Level Summary**:
  - [ ] **Red ("Protection Inactive")**: Displayed when Accessibility Service is disabled.
  - [ ] **Orange ("Action Recommended")**: Displayed when Accessibility is ON, but Battery Exemption, Device Admin, or Setup steps are pending.
  - [ ] **Green ("Optimal Protection")**: Displayed when core permissions, battery exemption, and admin are active.
- [ ] **Interactive Tapping**: Tapping any health item row directly opens its corresponding system settings screen.

---

## 4. Master Protection Switch & Password-Gated Actions (Phase 2 & 3)
- [ ] **Turning ON**: Switching protection to ON launches `GuardForegroundService` with notification "ZALNI Guard is active". No password required to turn ON.
- [ ] **Turning OFF**: Switching protection to OFF displays a password confirmation dialog.
  - [ ] Incorrect password displays inline error.
  - [ ] Correct password turns OFF protection and dismisses the foreground notification.
- [ ] **Keyword Addition**:
  - [ ] Input automatically trims spaces, lowercases, and collapses multiple spaces.
  - [ ] Reject duplicate words with inline error.
  - [ ] Reject words > 40 characters with inline error.
- [ ] **Keyword Deletion**: Tapping delete on a blocked keyword prompts for password confirmation before removing it from DataStore.

---

## 5. Core Blocking Engine & Home Redirect (Phase 3)
- [ ] **Latin Whole-Word Matching**:
  - [ ] With `sex` blocked, opening browser and viewing or typing `sex` triggers instant redirect to Home screen with "Blocked" toast.
  - [ ] Typing or viewing `Essex`, `Middlesex`, or `unisexual` does **NOT** trigger blocking.
- [ ] **Diacritics & Accents**: With `cafe` blocked, text with `café` is detected and blocked.
- [ ] **Multi-Word Phrases**: With `bad word` blocked, text containing `bad    word` with extra spaces is detected and blocked.
- [ ] **Non-Latin Script (Hindi/Devanagari)**: With `हिन्दी` blocked, sentences containing `कुछहिन्दीसामग्री` trigger immediate blocking.
- [ ] **Self-Exemption**: Typing or viewing blocked words inside ZALNI Guard itself never triggers redirection.
- [ ] **Loop Prevention**: After a block occurs, the 1.5-second cooldown prevents endless redirect loops.
- [ ] **Performance Limits**: Traversal capped at 1000 nodes and depth 30, with safe node recycling preventing memory leaks on 4 GB RAM devices.

---

## 6. Watchdog & Recovery (Phase 5)
- [ ] **Service Disabling Alert**: If the user or task killer disables the Accessibility Service while protection is ON, a high-priority notification `"ZALNI Guard is OFF - tap to fix"` appears. Tapping it opens Accessibility Settings.
- [ ] **15-Minute Periodic Check**: WorkManager runs in background without violating Android 12+ background start restrictions.
- [ ] **Device Reboot**:
  - [ ] Restart phone (`adb reboot`).
  - [ ] On boot completion, `BootReceiver` restores `GuardForegroundService` if protection was ON before restart.

---

## 7. Device Admin Anti-Uninstall (Phase 4 & 5)
- [ ] **Admin Activation**: Tapping "Anti-Uninstall Admin" prompts the user to activate Device Administrator privileges.
- [ ] **Uninstall Blocked**: Attempting to uninstall ZALNI Guard from device launcher or Settings displays warning and disables the "Uninstall" button until device admin is removed.

---

## 8. Memory Pressure & Release Optimization (Phase 5)
- [ ] **Low Memory Trim**: Under low RAM (`onTrimMemory` / `onLowMemory`), in-memory regex matcher cache is cleared and rebuilt lazily on demand.
- [ ] **ProGuard & Shrinking**: Release APK (`gradle assembleRelease`) has `minifyEnabled` and `shrinkResources` active with verified keep rules for all services, receivers, and workers.
