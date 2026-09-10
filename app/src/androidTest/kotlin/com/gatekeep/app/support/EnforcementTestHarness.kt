package com.gatekeep.app.support

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.gatekeep.app.R
import com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService
import com.gatekeep.app.enforcement.GatekeepNotificationHelper
import com.gatekeep.app.util.PermissionHelper

class EnforcementTestHarness(
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext,
) {
    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val packageName = context.packageName

    fun grantEnforcementPermissions() {
        uiDevice.executeShellCommand("appops set $packageName GET_USAGE_STATS allow")
        uiDevice.executeShellCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uiDevice.executeShellCommand(
                "pm grant $packageName android.permission.POST_NOTIFICATIONS",
            )
        }
        val a11yService = "$packageName/com.gatekeep.app.enforcement.ForegroundMonitorAccessibilityService"
        uiDevice.executeShellCommand(
            "settings put secure enabled_accessibility_services $a11yService",
        )
        uiDevice.executeShellCommand("settings put secure accessibility_enabled 1")
        uiDevice.executeShellCommand("dumpsys deviceidle whitelist +$packageName")
    }

    fun revokeUsagePermission() {
        uiDevice.executeShellCommand("appops set $packageName GET_USAGE_STATS deny")
    }

    fun revokeAccessibility() {
        uiDevice.executeShellCommand("settings put secure enabled_accessibility_services \"\"")
        uiDevice.executeShellCommand("settings put secure accessibility_enabled 0")
    }

    fun waitForAccessibilityConnected(timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ForegroundMonitorAccessibilityService.instance != null &&
                PermissionHelper.hasUsageStatsPermission(context) &&
                PermissionHelper.hasOverlayPermission(context) &&
                PermissionHelper.isAccessibilityEnabled(context)
            ) {
                return true
            }
            Thread.sleep(250)
        }
        return false
    }

    fun launchTargetA() {
        uiDevice.executeShellCommand("am start -n ${EnforcementTestPackages.TARGET_A_COMPONENT}")
        uiDevice.wait(Until.hasObject(By.pkg(EnforcementTestPackages.TARGET_A)), 5_000)
    }

    fun launchTargetB() {
        uiDevice.executeShellCommand("am start -n ${EnforcementTestPackages.TARGET_B_COMPONENT}")
        uiDevice.wait(Until.hasObject(By.pkg(EnforcementTestPackages.TARGET_B)), 5_000)
    }

    fun launchUnmonitored() {
        uiDevice.executeShellCommand(
            "monkey -p ${EnforcementTestPackages.UNMONITORED} -c android.intent.category.LAUNCHER 1",
        )
    }

    fun pressHome() {
        uiDevice.pressHome()
    }

    fun pressBack() {
        uiDevice.pressBack()
    }

    fun waitForOverlay(timeoutMs: Long = 10_000): Boolean {
        return uiDevice.wait(
            Until.hasObject(By.res(packageName, "block_message")),
            timeoutMs,
        )
    }

    fun waitForOverlayGone(timeoutMs: Long = 10_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!uiDevice.hasObject(By.res(packageName, "block_message"))) {
                return true
            }
            Thread.sleep(200)
        }
        return false
    }

    fun overlayMessageText(): String? =
        uiDevice.findObject(By.res(packageName, "block_message"))?.text

    fun overlayReasonText(): String? =
        uiDevice.findObject(By.res(packageName, "block_reason"))?.text

    fun isExtensionButtonsVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "extension_buttons"))

    fun isFrictionVisible(): Boolean =
        uiDevice.hasObject(By.res(packageName, "friction_container"))

    fun openNotifications(): Boolean = uiDevice.openNotification()

    fun waitForNotificationText(text: String, timeoutMs: Long = 10_000): Boolean =
        uiDevice.wait(Until.hasObject(By.textContains(text)), timeoutMs)

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
}
