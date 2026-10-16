package com.gatekeep.app.support

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import java.io.FileInputStream
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.gatekeep.app.R
import com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService
import com.gatekeep.app.enforcement.ForegroundStabilizationPolicy
import com.gatekeep.app.enforcement.GatekeepNotificationHelper
import com.gatekeep.app.util.PermissionHelper

class EnforcementTestHarness(
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext,
) {
    var onTargetLaunched: ((packageName: String, activityClassName: String) -> Unit)? = null
    var onNavigatedAway: (() -> Unit)? = null
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiDevice by lazy { UiDevice.getInstance(instrumentation) }
    private val packageName = context.packageName
    private var stayOnWhilePluggedInBackup: String? = null

    init {
        AnrWatchdog.configureUiAutomatorTimeouts()
    }

    companion object {
        private const val DUMP_TAG = "GatekeepOverlayClick"
        private const val PERMISSION_SETTLE_MS = 100L
        private const val APP_WAKE_SETTLE_MS = 250L
        private const val TARGET_LAUNCH_SETTLE_MS = 250L
        private const val POLL_INTERVAL_MS = 50L
        const val DEFAULT_OVERLAY_TIMEOUT_MS = 8_000L
        const val DEFAULT_OVERLAY_GONE_TIMEOUT_MS = 8_000L
        const val DEFAULT_A11Y_TIMEOUT_MS = 8_000L
        const val DEFAULT_NOTIFICATION_TIMEOUT_MS = 8_000L

        @Volatile
        private var didReboundAccessibilityForWindows = false
    }

    private fun shell(command: String): String {
        val output = instrumentation.uiAutomation.executeShellCommand(command)
        return when (output) {
            is String -> output.trim()
            is ParcelFileDescriptor -> output.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().readText().trim()
            }
            is java.io.FileDescriptor -> FileInputStream(output).bufferedReader().readText().trim()
            else -> uiDevice.executeShellCommand(command).trim()
        }
    }

    /** Emulator hardware keyboard + Gboard superpacks can ANR key dispatch during PIN/math. */
    fun disableSoftImeForTests() {
        shell("settings put secure show_ime_with_hard_keyboard 0")
    }

    fun grantUsageStatsAndOverlay() {
        shell("cmd appops set $packageName GET_USAGE_STATS allow")
        shell("cmd appops set $packageName SYSTEM_ALERT_WINDOW allow")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        shell("dumpsys deviceidle whitelist +$packageName")
        Thread.sleep(PERMISSION_SETTLE_MS)
    }

    fun grantEnforcementPermissions() {
        grantUsageStatsAndOverlay()
        grantAccessibilityViaShell()
    }

    fun isAccessibilityHealthy(): Boolean =
        PermissionHelper.isAccessibilityEnabled(context) &&
            ForegroundMonitorAccessibilityService.instance != null

    fun canReadAccessibilityWindows(): Boolean =
        ForegroundMonitorAccessibilityService.instance
            ?.overlayWindowSnapshots()
            ?.isNotEmpty() == true

    /**
     * Cross-app tests need overlay + usage stats. The accessibility service uses an
     * EntryPoint (not @AndroidEntryPoint) so it can bind under the Hilt test application.
     */
    fun grantEnforcementPermissionsForCrossAppTests() {
        grantUsageStatsAndOverlay()
        // After an emulator crash/reboot the secure a11y setting is 0; UiAutomator then
        // cannot see FLAG_NOT_FOCUSABLE overlays. Re-enable the service via shell.
        // Do not bounce accessibility_enabled 0→1 when the service is already bound:
        // that disconnects windows=[] for the next overlay tests.
        if (!isAccessibilityHealthy()) {
            grantAccessibilityViaShell()
            waitForAccessibilityServiceConnected(timeoutMs = DEFAULT_A11Y_TIMEOUT_MS)
        }
        waitUntilAccessibilityWindowsReadable()
        collapseTransientSystemUi()
    }

    private fun grantAccessibilityViaShell() {
        if (isAccessibilityHealthy()) return
        val a11yService =
            "$packageName/com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService"
        val current = shell("settings get secure enabled_accessibility_services")
        val listed = current.contains(a11yService)
        if (!listed) {
            val merged = when {
                current.isBlank() || current == "null" -> a11yService
                else -> "$current:$a11yService"
            }
            shell("settings put secure enabled_accessibility_services $merged")
        }
        shell("settings put secure accessibility_enabled 1")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            shell("cmd accessibility enable-service $a11yService")
        }
    }

    private fun waitForAccessibilityServiceConnected(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0) return isAccessibilityHealthy()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isAccessibilityHealthy()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return isAccessibilityHealthy()
    }

    fun waitUntilAccessibilityWindowsReadable(timeoutMs: Long = DEFAULT_A11Y_TIMEOUT_MS): Boolean {
        val readable = ensureAccessibilityWindowsReadable(timeoutMs)
        // TARGET_A is com.gatekeep.app. Leaving MainActivity in front makes a
        // no-injection Target A launch a same-package no-op (obs01 overlay missing).
        uiDevice.pressHome()
        return readable
    }

    private fun ensureAccessibilityWindowsReadable(timeoutMs: Long): Boolean {
        if (pollAccessibilityWindows(timeoutMs / 4)) return true
        wakeAppForAccessibilityBinding()
        if (pollAccessibilityWindows(timeoutMs / 4)) return true
        uiDevice.pressHome()
        if (pollAccessibilityWindows(timeoutMs / 4)) return true
        if (!didReboundAccessibilityForWindows) {
            didReboundAccessibilityForWindows = true
            reboundAccessibilityForInteractiveWindows()
            wakeAppForAccessibilityBinding()
            uiDevice.pressHome()
            if (pollAccessibilityWindows(timeoutMs / 2)) return true
        }
        return canReadAccessibilityWindows()
    }

    private fun pollAccessibilityWindows(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0L) return canReadAccessibilityWindows()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (canReadAccessibilityWindows()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return canReadAccessibilityWindows()
    }

    private fun reboundAccessibilityForInteractiveWindows() {
        val a11yService =
            "$packageName/com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService"
        shell("settings put secure accessibility_enabled 0")
        shell("settings put secure enabled_accessibility_services $a11yService")
        shell("settings put secure accessibility_enabled 1")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            shell("cmd accessibility enable-service $a11yService")
        }
        waitForAccessibilityServiceConnected(timeoutMs = DEFAULT_A11Y_TIMEOUT_MS)
    }

    private fun enableAccessibilityViaSettingsUi(): Boolean {
        uiDevice.pressHome()
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
        )
        if (!uiDevice.wait(Until.hasObject(By.pkg("com.android.settings")), 5_000)) {
            returnToTestApp()
            return false
        }
        if (!openGatekeepAccessibilityEntry()) {
            returnToTestApp()
            return false
        }
        waitForIdleBrief()
        if (!toggleAccessibilitySwitchOnDetailScreen()) {
            returnToTestApp()
            return false
        }
        confirmAccessibilityConsentDialog()
        returnToTestApp()
        Thread.sleep(400)
        wakeAppForAccessibilityBinding()
        return PermissionHelper.isAccessibilityEnabled(context)
    }

    private fun openGatekeepAccessibilityEntry(): Boolean {
        val settings = By.pkg("com.android.settings")
        val labels = listOf("Gatekeep", "screen time limits", "Downloaded apps")
        repeat(5) {
            for (label in labels) {
                val textNode = uiDevice.findObject(settings.textContains(label)) ?: continue
                var clickable = textNode
                var parent = textNode.parent
                repeat(5) {
                    if (parent != null && parent.isClickable) {
                        clickable = parent
                        return@repeat
                    }
                    parent = parent?.parent
                }
                clickable.click()
                waitForIdleBrief()
                return true
            }
            uiDevice.findObject(settings.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            waitForIdleBrief()
        }
        return false
    }

    private fun toggleAccessibilitySwitchOnDetailScreen(): Boolean {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            val toggle = findAccessibilityToggle()
            if (toggle == null) {
                Thread.sleep(150)
                continue
            }
            if (!isToggleChecked()) {
                findAccessibilityToggle()?.let { clickAccessibilityToggle(it) }
                confirmAccessibilityConsentDialog()
                waitForIdleBrief()
                return true
            }
            if (ForegroundMonitorAccessibilityService.instance != null) {
                return true
            }
            findAccessibilityToggle()?.let { clickAccessibilityToggle(it) }
            waitForIdleBrief()
            Thread.sleep(150)
        }
        return false
    }

    private fun isToggleChecked(): Boolean = findAccessibilityToggle()?.isChecked == true

    private fun findAccessibilityToggle(): androidx.test.uiautomator.UiObject2? {
        val settings = By.pkg("com.android.settings")
        return uiDevice.findObject(By.res("android", "switch_widget"))
            ?: uiDevice.findObject(settings.clazz("android.widget.Switch"))
            ?: uiDevice.findObject(settings.checkable(true))
    }

    private fun clickAccessibilityToggle(toggle: androidx.test.uiautomator.UiObject2) {
        val center = toggle.visibleCenter
        uiDevice.click(center.x, center.y)
    }

    private fun confirmAccessibilityConsentDialog() {
        val labels = listOf("Allow", "Turn on", "Start", "OK", "Accept")
        uiDevice.wait(
            Until.hasObject(By.textContains("accessibility")),
            5_000,
        )
        for (label in labels) {
            uiDevice.findObject(By.text(label))?.click()?.let { return }
            uiDevice.findObject(By.textContains(label))?.click()?.let { return }
        }
    }

    private fun returnToTestApp() {
        repeat(3) { uiDevice.pressBack() }
        wakeAppForAccessibilityBinding()
    }

    fun wakeAppForAccessibilityBinding() {
        // Do not force-stop: instrumentation runs in the same package and would be killed.
        shell("am start -n $packageName/.MainActivity --activity-clear-top")
        Thread.sleep(APP_WAKE_SETTLE_MS)
    }

    fun waitForPermissionGrants(timeoutMs: Long = 4_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (PermissionHelper.hasUsageStatsPermission(context) &&
                PermissionHelper.hasOverlayPermission(context) &&
                PermissionHelper.isAccessibilityEnabled(context)
            ) {
                return true
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    fun grantEnforcementPermissionsAndWait(timeoutMs: Long = 5_000): Boolean {
        grantEnforcementPermissions()
        return waitForPermissionGrants(timeoutMs)
    }

    fun revokeUsagePermission() {
        shell("cmd appops set $packageName GET_USAGE_STATS ignore")
        Thread.sleep(PERMISSION_SETTLE_MS)
    }

    fun revokeAccessibility() {
        shell("settings put secure enabled_accessibility_services \"\"")
        shell("settings put secure accessibility_enabled 0")
    }

    fun isAccessibilityServiceEnabled(): Boolean =
        PermissionHelper.isAccessibilityEnabled(context)

    fun waitForAccessibilityConnected(timeoutMs: Long = DEFAULT_A11Y_TIMEOUT_MS): Boolean =
        hasCoreEnforcementPermissionsGranted() &&
            waitForAccessibilityServiceConnected(timeoutMs)

    fun collapseTransientSystemUi() {
        failIfAnr("collapseTransientSystemUi")
        shell("cmd statusbar collapse")
    }

    fun launchTargetA() {
        failIfAnr("launchTargetA")
        uiDevice.wakeUp()
        dismissKeyguard()
        collapseTransientSystemUi()
        run {
            repeat(3) {
                collapseTransientSystemUi()
                shell("am start -n ${EnforcementTestPackages.TARGET_A_COMPONENT}")
                if (uiDevice.wait(
                        Until.hasObject(By.text(EnforcementTestPackages.TARGET_A_LABEL)),
                        2_000,
                    )
                ) {
                    return@run
                }
            }
        }
        onTargetLaunched?.invoke(
            EnforcementTestPackages.TARGET_A,
            "com.gatekeep.app.testsupport.EnforcementTargetActivity",
        )
    }

    fun launchTargetB() {
        failIfAnr("launchTargetB")
        uiDevice.wakeUp()
        dismissKeyguard()
        collapseTransientSystemUi()
        run {
            repeat(3) {
                collapseTransientSystemUi()
                shell("am start -n ${EnforcementTestPackages.TARGET_B_COMPONENT}")
                if (uiDevice.wait(
                        Until.hasObject(By.pkg(EnforcementTestPackages.TARGET_B)),
                        2_000,
                    )
                ) {
                    return@run
                }
            }
        }
        onTargetLaunched?.invoke(
            EnforcementTestPackages.TARGET_B,
            "com.android.settings.Settings",
        )
    }

    fun launchUnmonitored() {
        shell("monkey -p ${EnforcementTestPackages.UNMONITORED} -c android.intent.category.LAUNCHER 1")
        uiDevice.wait(Until.hasObject(By.pkg(EnforcementTestPackages.UNMONITORED)), TARGET_LAUNCH_SETTLE_MS)
        onTargetLaunched?.invoke(EnforcementTestPackages.UNMONITORED, "")
    }

    fun pressHome() {
        uiDevice.pressHome()
        onNavigatedAway?.invoke()
    }

    fun pressRecents() {
        uiDevice.pressRecentApps()
        Thread.sleep(TARGET_LAUNCH_SETTLE_MS)
        onNavigatedAway?.invoke()
    }

    fun resumeFromRecents() {
        uiDevice.pressRecentApps()
        Thread.sleep(TARGET_LAUNCH_SETTLE_MS)
        onNavigatedAway?.invoke()
        Thread.sleep(400)
        clickRecentsTaskCard()
        Thread.sleep(TARGET_LAUNCH_SETTLE_MS)
        if (isRecentsOrLauncherForeground()) {
            shell("am start -n ${EnforcementTestPackages.TARGET_A_COMPONENT}")
            uiDevice.wait(
                Until.hasObject(By.pkg(EnforcementTestPackages.TARGET_A)),
                3_000,
            )
        }
        Thread.sleep(TARGET_LAUNCH_SETTLE_MS)
        onTargetLaunched?.invoke(
            EnforcementTestPackages.TARGET_A,
            "com.gatekeep.app.testsupport.EnforcementTargetActivity",
        )
    }

    private fun clickRecentsTaskCard(): Boolean {
        val selectors = listOf(
            By.text(EnforcementTestPackages.TARGET_A_LABEL),
            By.textContains("Gatekeep"),
            By.descContains("Gatekeep"),
            By.clazz("com.android.quickstep.views.TaskView"),
            By.res("com.android.launcher3", "snapshot"),
            By.res("com.google.android.apps.nexuslauncher", "snapshot"),
        )
        for (selector in selectors) {
            val node = uiDevice.findObject(selector) ?: continue
            node.click()
            return true
        }
        uiDevice.click(uiDevice.displayWidth / 2, (uiDevice.displayHeight * 0.4).toInt())
        return true
    }

    private fun isRecentsOrLauncherForeground(): Boolean {
        val pkg = uiDevice.currentPackageName ?: return true
        return pkg == "com.android.systemui" ||
            pkg == "com.android.launcher" ||
            pkg == "com.android.launcher3" ||
            pkg == "com.google.android.apps.nexuslauncher"
    }

    /** Reset platform usage counters so prior test runs do not inflate merged usage snapshots. */
    fun resetTestTargetUsageStats() {
        shell("cmd usagestats reset")
        Thread.sleep(PERMISSION_SETTLE_MS)
    }

    fun pressBack() {
        uiDevice.pressBack()
    }

    fun waitForOverlay(timeoutMs: Long = DEFAULT_OVERLAY_TIMEOUT_MS): Boolean {
        collapseTransientSystemUi()
        failIfAnr("waitForOverlay")
        return uiDevice.wait(
            Until.hasObject(By.res(packageName, "block_message")),
            timeoutMs,
        )
    }

    fun waitForOverlayGone(timeoutMs: Long = DEFAULT_OVERLAY_GONE_TIMEOUT_MS): Boolean {
        collapseTransientSystemUi()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            failIfAnr("waitForOverlayGone")
            val visible = uiDevice.hasObject(By.res(packageName, "block_message")) ||
                overlayMessageText() != null
            if (!visible) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return !uiDevice.hasObject(By.res(packageName, "block_message")) &&
            overlayMessageText() == null
    }

    /** True if the block overlay appears at any poll during [windowMs] (catches a flash). */
    fun overlayAppearedDuring(windowMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + windowMs
        while (System.currentTimeMillis() < deadline) {
            if (uiDevice.hasObject(By.res(packageName, "block_message"))) {
                return true
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    fun overlayMessageText(): String? {
        repeat(3) {
            try {
                return uiDevice.findObject(By.res(packageName, "block_message"))?.text
            } catch (_: StaleObjectException) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
        return null
    }

    /**
     * Launches are observed from the device, not injected as foreground.
     * Keep [onNavigatedAway] so Home/Recents still poll; overlay hide cannot wait on
     * accessibility events that never arrive if the service instance is unbound.
     */
    fun useRealForegroundDetection() {
        onTargetLaunched = null
    }

    fun countdownText(): String? {
        repeat(3) {
            try {
                return uiDevice.findObject(By.res(packageName, "wait_countdown"))?.text
            } catch (_: StaleObjectException) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
        return null
    }

    fun countdownSeconds(): Int? =
        countdownText()?.filter(Char::isDigit)?.toIntOrNull()

    fun hudTimeText(): String? {
        repeat(3) {
            try {
                return uiDevice.findObject(By.res(packageName, "hud_time_text"))?.text
            } catch (_: StaleObjectException) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
        return null
    }

    fun sessionRemainingSeconds(): Int? =
        parseLeadingSeconds(hudTimeText())
            ?: parseLeadingSeconds(countdownNotificationBody())

    fun waitForCountdown(timeoutMs: Long = DEFAULT_OVERLAY_TIMEOUT_MS): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            countdownSeconds()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    fun waitForSessionRemainingSeconds(timeoutMs: Long = DEFAULT_OVERLAY_TIMEOUT_MS): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            sessionRemainingSeconds()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    private fun parseLeadingSeconds(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""(\d+)\s*s\b""").find(text)
            ?: Regex("""\b(\d+):(\d{2})\b""").find(text)
        if (match == null) return text.filter(Char::isDigit).takeIf { it.isNotEmpty() }?.toIntOrNull()
        return if (match.groupValues.size >= 3 && match.groupValues[2].isNotEmpty()) {
            match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
        } else {
            match.groupValues[1].toIntOrNull()
        }
    }

    fun waitForOverlayElapsedMs(timeoutMs: Long = DEFAULT_OVERLAY_TIMEOUT_MS): Long? {
        val start = System.currentTimeMillis()
        if (!waitForOverlay(timeoutMs)) return null
        return System.currentTimeMillis() - start
    }

    fun sleepDevice() {
        disableStayAwakeForSleep()
        forceScreenOff()
    }

    /** Sleeps the device and returns the last readable overlay countdown, if any. */
    fun sleepDeviceCapturingCountdown(): Int? {
        var last = countdownSeconds()
        disableStayAwakeForSleep()
        forceScreenOff { countdownSeconds()?.let { last = it } }
        countdownSeconds()?.let { last = it }
        return last
    }

    /**
     * Sleeps the device and returns session remaining at the last still-on sample.
     * [forceScreenOff] can take seconds; do not treat that burn as lock-freeze failure.
     */
    fun sleepDeviceCapturingSessionRemaining(): Int? {
        var last = sessionRemainingSeconds()
        disableStayAwakeForSleep()
        forceScreenOff { sessionRemainingSeconds()?.let { last = it } }
        return last
    }

    /** @return true when the screen is off. */
    fun sleepAndConfirmOff(): Boolean {
        sleepDevice()
        return !uiDevice.isScreenOn
    }

    fun isScreenOn(): Boolean = uiDevice.isScreenOn

    /**
     * After AVD reboot, `boot_completed=1` is not enough: credential-encrypted
     * SharedPreferences throw until the user is unlocked (`lock_settings` up).
     */
    fun awaitCredentialEncryptedStorage(timeoutMs: Long = 60_000) {
        dismissKeyguard()
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                context.getSharedPreferences("_ce_probe", Context.MODE_PRIVATE)
                return
            } catch (e: IllegalStateException) {
                last = e
                if (e.message?.contains("credential encrypted") != true) throw e
            }
            Thread.sleep(POLL_INTERVAL_MS)
            dismissKeyguard()
        }
        throw IllegalStateException(
            "credential-encrypted storage still locked after ${timeoutMs}ms",
            last,
        )
    }

    fun wakeDevice() {
        failIfAnr("wakeDevice")
        restoreStayAwake()
        runCatching { uiDevice.wakeUp() }
        dismissKeyguard()
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && (!uiDevice.isScreenOn || isKeyguardLocked())) {
            dismissKeyguard()
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    fun restoreStayAwake() {
        val backup = stayOnWhilePluggedInBackup ?: "7"
        shell("settings put global stay_on_while_plugged_in $backup")
        shell("settings put system screen_off_timeout 1800000")
        shell("svc power stayon true")
    }

    /** Wake the display without dismissing the keyguard (RES-05). */
    fun wakeToLockScreen() {
        restoreStayAwake()
        runCatching { uiDevice.wakeUp() }
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && !uiDevice.isScreenOn) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun disableStayAwakeForSleep() {
        if (stayOnWhilePluggedInBackup == null) {
            val current = shell("settings get global stay_on_while_plugged_in")
            stayOnWhilePluggedInBackup = current.takeIf { it.isNotBlank() && it != "null" } ?: "7"
        }
        shell("settings put global stay_on_while_plugged_in 0")
        shell("svc power stayon false")
    }

    private fun forceScreenOff(onStillOn: () -> Unit = {}) {
        repeat(4) {
            if (!uiDevice.isScreenOn) return
            onStillOn()
            runCatching { uiDevice.sleep() }
            if (uiDevice.isScreenOn) {
                shell("input keyevent 26")
            }
            if (uiDevice.isScreenOn) {
                shell("input keyevent 223")
            }
            val deadline = System.currentTimeMillis() + 1_500
            while (System.currentTimeMillis() < deadline && uiDevice.isScreenOn) {
                onStillOn()
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
    }

    fun dismissKeyguard() {
        // After AVD crash/reboot the keyguard is on; Paused phase skips overlay evaluate.
        shell("locksettings set-disabled true")
        shell("wm dismiss-keyguard")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("input keyevent 82")
    }

    fun isKeyguardLocked(): Boolean =
        context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true

    fun clearLogcat() {
        shell("logcat -c")
    }

    fun gatekeepFatalCrash(): String? {
        val log = shell("logcat -d -s AndroidRuntime:E")
        if (!log.contains("FATAL EXCEPTION") || !log.contains(packageName)) return null
        return log.lineSequence().take(30).joinToString("\n")
    }

    fun screenDiagnostics(): String {
        val pkg = runCatching { uiDevice.currentPackageName }.getOrNull()
        val crash = gatekeepFatalCrash()?.lineSequence()?.firstOrNull()
        val windows = ForegroundMonitorAccessibilityService.instance?.overlayWindowSnapshots()
            .orEmpty()
            .take(4)
            .joinToString(" | ") { "${it.packageName.substringAfterLast('.')}:${it.isFocused}/${it.isActive}" }
        return "pkg=$pkg overlay=${overlayMessageText()} countdown=${countdownText()} " +
            "hud=${hudTimeText()} notif=${countdownNotificationBody()} " +
            "screenOn=${isScreenOn()} keyguard=${isKeyguardLocked()} windows=[$windows] " +
            "anr=${AnrWatchdog.seen.get()} crash=$crash"
    }

    fun overlayReasonText(): String? =
        uiDevice.findObject(By.res(packageName, "block_reason"))?.text

    fun isExtensionButtonsVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "extension_buttons"))

    fun clickOverlayExtensionMinutes(minutes: Int): Boolean {
        val label = context.getString(R.string.extension_minutes_format, minutes)
        val clicked = clickExtensionButton(label, minutes)
        if (!clicked) {
            Log.e(
                DUMP_TAG,
                "click missed label=$label extVisible=${isExtensionButtonsVisible()} ${screenDiagnostics()}",
            )
            return false
        }
        waitForIdleBrief()
        val gone = waitForOverlayGone()
        if (!gone) {
            Log.e(
                DUMP_TAG,
                "overlay stayed after $label extVisible=${isExtensionButtonsVisible()} ${screenDiagnostics()}",
            )
        }
        return gone
    }

    private fun clickExtensionButton(label: String, minutes: Int): Boolean {
        val deadline = System.currentTimeMillis() + DEFAULT_OVERLAY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            collapseTransientSystemUi()
            try {
                val button = uiDevice.findObject(By.text(label))
                    ?: uiDevice.findObject(By.textContains("+$minutes"))
                if (button != null) {
                    button.click()
                    return true
                }
                uiDevice.findObject(By.res(packageName, "overlay_scroll"))
                    ?.scroll(Direction.DOWN, 1.0f)
            } catch (_: StaleObjectException) {
                // Overlay rebuilt between find and click; retry.
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    fun clickOverlayNoLimitToday(): Boolean {
        val label = context.getString(R.string.overlay_no_limit_today)
        val deadline = System.currentTimeMillis() + DEFAULT_OVERLAY_TIMEOUT_MS
        var clicked = false
        while (System.currentTimeMillis() < deadline && !clicked) {
            collapseTransientSystemUi()
            try {
                val button = uiDevice.findObject(By.text(label))
                if (button != null) {
                    button.click()
                    clicked = true
                } else {
                    uiDevice.findObject(By.res(packageName, "overlay_scroll"))
                        ?.scroll(Direction.DOWN, 1.0f)
                }
            } catch (_: StaleObjectException) {
                // Overlay rebuilt between find and click; retry.
            }
            if (!clicked) Thread.sleep(POLL_INTERVAL_MS)
        }
        if (!clicked) {
            Log.e(
                DUMP_TAG,
                "no-limit click missed label=$label extVisible=${isExtensionButtonsVisible()} ${screenDiagnostics()}",
            )
            return false
        }
        waitForIdleBrief()
        val gone = waitForOverlayGone()
        if (!gone) {
            Log.e(
                DUMP_TAG,
                "overlay stayed after no-limit extVisible=${isExtensionButtonsVisible()} ${screenDiagnostics()}",
            )
        }
        return gone
    }

    fun countdownNotificationBody(): String? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = manager.activeNotifications.firstOrNull {
            it.id == GatekeepNotificationHelper.COUNTDOWN_NOTIFICATION_ID
        }
        val extras = active?.notification?.extras ?: return null
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
    }

    fun countdownNotificationContainsInfinity(): Boolean {
        val infinity = context.getString(R.string.duration_infinity)
        return countdownNotificationBody()?.contains(infinity) == true
    }

    fun isFrictionVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "friction_container")) ||
            uiDevice.hasObject(By.res(packageName, "wait_countdown")) ||
            uiDevice.hasObject(By.res(packageName, "friction_prompt"))

    fun waitForFrictionGone(timeoutMs: Long = DEFAULT_OVERLAY_GONE_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) { !isFrictionVisible() }

    fun assertNoOverlay(timeoutMs: Long = DEFAULT_OVERLAY_GONE_TIMEOUT_MS): Boolean =
        waitForOverlayGone(timeoutMs)

    fun isOpenWaitVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "wait_countdown"))

    fun waitForOpenFriction(timeoutMs: Long = DEFAULT_OVERLAY_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) { isFrictionVisible() || isOpenWaitVisible() }

    fun assertHardBlockWithoutOpenWait(openWaitSec: Int, timeoutMs: Long = DEFAULT_OVERLAY_TIMEOUT_MS): Boolean {
        val overlayShown = waitForOverlay(timeoutMs)
        if (!overlayShown) return false
        val frictionVisibleEarly = isOpenWaitVisible()
        if (frictionVisibleEarly) return false
        waitForElapsedMs(openWaitSec * 1_000L + 200L)
        return waitForOverlay(timeoutMs = 1_500) && !isOpenWaitVisible()
    }

    fun assertOverlayStable(stableMs: Long = 500, maxTextChanges: Int = 1): Boolean {
        var lastText: String? = null
        var changes = 0
        val deadline = System.currentTimeMillis() + stableMs
        while (System.currentTimeMillis() < deadline) {
            val text = overlayMessageText()
            if (text != null && text != lastText) {
                changes++
                lastText = text
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return changes <= maxTextChanges
    }

    fun assertCountdownDecreased(sampleMs: Long = 600): Boolean {
        val first = overlayMessageText() ?: return false
        waitForElapsedMs(sampleMs)
        val second = overlayMessageText() ?: return false
        return first != second
    }

    fun openNotifications(): Boolean = uiDevice.openNotification()

    fun waitForNotificationText(text: String, timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean =
        uiDevice.wait(Until.hasObject(By.textContains(text)), timeoutMs)

    fun awaitCondition(
        timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            failIfAnr("awaitCondition")
            if (condition()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return condition()
    }

    fun waitForServiceNotification(timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) { hasActiveServiceNotification() }

    fun waitForCountdownNotification(timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) { hasActiveCountdownNotification() }

    fun waitForCountdownNotificationGone(timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) { !hasActiveCountdownNotification() }

    /** Poll until a wall-clock timer elapses; returns as soon as [minElapsedMs] is met. */
    fun waitForElapsedMs(minElapsedMs: Long, timeoutMs: Long = minElapsedMs + 500): Boolean {
        val start = System.currentTimeMillis()
        val deadline = start + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (System.currentTimeMillis() - start >= minElapsedMs) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return System.currentTimeMillis() - start >= minElapsedMs
    }

    fun hasActiveServiceNotification(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.id == GatekeepNotificationHelper.SERVICE_NOTIFICATION_ID
        }
    }

    fun hasActiveCountdownNotification(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.id == GatekeepNotificationHelper.COUNTDOWN_NOTIFICATION_ID
        }
    }

    fun activeNotification(notificationId: Int): Notification? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.firstOrNull { it.id == notificationId }?.notification
    }

    fun hasNotificationContentIntent(notificationId: Int): Boolean =
        activeNotification(notificationId)?.contentIntent != null

    fun launchNotificationContentIntent(notificationId: Int): Boolean {
        val pendingIntent = activeNotification(notificationId)?.contentIntent ?: return false
        return try {
            pendingIntent.send()
            waitForIdleBrief()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun waitForApproachingLimitNotification(timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) {
            activeNotification(GatekeepNotificationHelper.APPROACHING_LIMIT_ID) != null
        } || waitForNotificationText(
            context.getString(R.string.approaching_limit_title),
            timeoutMs,
        )

    fun waitForWeeklyReportNotification(timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) {
            activeNotification(GatekeepNotificationHelper.WEEKLY_REPORT_ID) != null
        } || waitForNotificationText(
            context.getString(R.string.weekly_report_title),
            timeoutMs,
        )

    fun waitForCurrentUsageScreen(timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean {
        val title = context.getString(R.string.profile_current_usage)
        return uiDevice.wait(Until.hasObject(By.text(title)), timeoutMs)
    }

    fun waitForStatsScreen(timeoutMs: Long = DEFAULT_NOTIFICATION_TIMEOUT_MS): Boolean {
        val title = context.getString(R.string.statistics)
        return uiDevice.wait(Until.hasObject(By.text(title)), timeoutMs)
    }

    fun swipeNotificationAway(textContains: String) {
        openNotifications()
        val notification = uiDevice.findObject(By.textContains(textContains))
        if (notification != null) {
            notification.swipe(Direction.LEFT, 0.5f)
        }
        uiDevice.pressBack()
    }

    fun submitOverlayMathAnswer(answer: String) {
        shell("settings put secure show_ime_with_hard_keyboard 0")
        repeat(3) {
            try {
                val input = uiDevice.findObject(By.res(packageName, "friction_input")) ?: return
                // Click is required for the PIN to stick; IME is suppressed above so
                // Gboard handwriting packs do not ANR the suite.
                input.click()
                input.text = answer
                uiDevice.findObject(By.res(packageName, "friction_submit"))?.click()
                return
            } catch (_: StaleObjectException) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
    }

    fun clickOverlayBack() {
        uiDevice.findObject(By.res(packageName, "block_back_btn"))?.click()
    }

    fun clickOverlayContinue() {
        uiDevice.findObject(By.res(packageName, "block_continue_btn"))?.click()
    }

    fun sleepMs(ms: Long) {
        Thread.sleep(ms)
    }

    val device: UiDevice get() = uiDevice

    fun hasCoreEnforcementPermissionsGranted(): Boolean =
        PermissionHelper.hasUsageStatsPermission(context) &&
            PermissionHelper.hasOverlayPermission(context)

    fun hasEnforcementPermissionsGranted(): Boolean =
        hasCoreEnforcementPermissionsGranted() &&
            PermissionHelper.isAccessibilityEnabled(context)

    fun permissionDiagnostics(): String =
        buildString {
            append("usage=").append(PermissionHelper.hasUsageStatsPermission(context))
            append(" overlay=").append(PermissionHelper.hasOverlayPermission(context))
            append(" a11y=").append(PermissionHelper.isAccessibilityEnabled(context))
            append(" service=").append(ForegroundMonitorAccessibilityService.instance != null)
        }

    fun failIfAnr(where: String) {
        AnrWatchdog.failIfSeen(where)
    }

    private fun waitForIdleBrief(timeoutMs: Long = 1_000L) {
        failIfAnr("waitForIdle")
        uiDevice.waitForIdle(timeoutMs)
    }
}
