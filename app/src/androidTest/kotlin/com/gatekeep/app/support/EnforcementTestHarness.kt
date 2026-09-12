package com.gatekeep.app.support

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import java.io.FileInputStream
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.gatekeep.app.R
import com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService
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

    companion object {
        private const val PERMISSION_SETTLE_MS = 100L
        private const val APP_WAKE_SETTLE_MS = 250L
        private const val TARGET_LAUNCH_SETTLE_MS = 250L
        private const val POLL_INTERVAL_MS = 50L
        const val DEFAULT_OVERLAY_TIMEOUT_MS = 5_000L
        const val DEFAULT_OVERLAY_GONE_TIMEOUT_MS = 2_500L
        const val DEFAULT_A11Y_TIMEOUT_MS = 8_000L
        const val DEFAULT_NOTIFICATION_TIMEOUT_MS = 2_000L
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

    /**
     * Cross-app enforcement tests prefer UsageStats polling + harness foreground callbacks.
     * Accessibility is attempted briefly; repeated shell enable can crash Hilt under instrumentation.
     */
    fun grantEnforcementPermissionsForCrossAppTests() {
        grantUsageStatsAndOverlay()
        // Do not launch MainActivity from shell here — it starts the app process outside
        // HiltAndroidRule and crashes under instrumentation. Cross-app tests use polling
        // plus harness.onTargetLaunched when accessibility is unavailable.
        waitForAccessibilityServiceConnected(timeoutMs = 500)
    }

    private fun grantAccessibilityViaShell() {
        val a11yService =
            "$packageName/com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService"
        val current = shell("settings get secure enabled_accessibility_services")
        val merged = when {
            current.isBlank() || current == "null" -> a11yService
            current.contains(a11yService) -> current
            else -> "$current:$a11yService"
        }
        shell("settings put secure enabled_accessibility_services $merged")
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
        uiDevice.waitForIdle()
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
                uiDevice.waitForIdle(400)
                return true
            }
            uiDevice.findObject(settings.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            uiDevice.waitForIdle(250)
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
                uiDevice.waitForIdle(400)
                return true
            }
            if (ForegroundMonitorAccessibilityService.instance != null) {
                return true
            }
            findAccessibilityToggle()?.let { clickAccessibilityToggle(it) }
            uiDevice.waitForIdle(250)
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

    fun launchTargetA() {
        uiDevice.wakeUp()
        shell("am start -n ${EnforcementTestPackages.TARGET_A_COMPONENT}")
        uiDevice.wait(Until.hasObject(By.pkg(EnforcementTestPackages.TARGET_A)), TARGET_LAUNCH_SETTLE_MS)
        onTargetLaunched?.invoke(
            EnforcementTestPackages.TARGET_A,
            "com.gatekeep.app.testsupport.EnforcementTargetActivity",
        )
    }

    fun launchTargetB() {
        uiDevice.wakeUp()
        shell("am start -n ${EnforcementTestPackages.TARGET_B_COMPONENT}")
        uiDevice.wait(Until.hasObject(By.pkg(EnforcementTestPackages.TARGET_B)), TARGET_LAUNCH_SETTLE_MS)
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
        Thread.sleep(TARGET_LAUNCH_SETTLE_MS)
        onNavigatedAway?.invoke()
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
        return uiDevice.wait(
            Until.hasObject(By.res(packageName, "block_message")),
            timeoutMs,
        )
    }

    fun waitForOverlayGone(timeoutMs: Long = DEFAULT_OVERLAY_GONE_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!uiDevice.hasObject(By.res(packageName, "block_message"))) {
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

    fun overlayReasonText(): String? =
        uiDevice.findObject(By.res(packageName, "block_reason"))?.text

    fun isExtensionButtonsVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "extension_buttons"))

    fun isFrictionVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "friction_container"))

    fun waitForFrictionGone(timeoutMs: Long = DEFAULT_OVERLAY_GONE_TIMEOUT_MS): Boolean =
        awaitCondition(timeoutMs) { !isFrictionVisible() }

    fun assertNoOverlay(timeoutMs: Long = DEFAULT_OVERLAY_GONE_TIMEOUT_MS): Boolean =
        waitForOverlayGone(timeoutMs)

    fun isOpenWaitVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "friction_container")) &&
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

    fun swipeNotificationAway(textContains: String) {
        openNotifications()
        val notification = uiDevice.findObject(By.textContains(textContains))
        if (notification != null) {
            notification.swipe(Direction.LEFT, 0.5f)
        }
        uiDevice.pressBack()
    }

    fun submitOverlayMathAnswer(answer: String) {
        val input = uiDevice.findObject(By.res(packageName, "friction_input"))
        input?.click()
        input?.text = answer
        uiDevice.findObject(By.res(packageName, "friction_submit"))?.click()
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

    fun sleepDevice() {
        uiDevice.sleep()
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
}
