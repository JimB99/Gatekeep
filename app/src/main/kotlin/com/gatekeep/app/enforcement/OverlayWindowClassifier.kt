package com.gatekeep.app.enforcement

enum class OverlayWindowKind {
    BlockedApp,
    Recents,
    Launcher,
    OtherApp,
    IncomingCall,
    Shade,
    TransientSystemUi,
    Unknown,
}

data class OverlayWindowSnapshot(
    val packageName: String,
    val className: String?,
    val isOverlay: Boolean,
    val isFocused: Boolean = false,
    val isActive: Boolean = false,
    val windowType: Int = 0,
)

object OverlayWindowClassifier {

    val imePackages = setOf(
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
    )

    fun classify(
        packageName: String,
        className: String?,
        blockedPackage: String?,
    ): OverlayWindowKind {
        val cls = className.orEmpty()
        if (isTransientSystemUi(packageName, cls)) return OverlayWindowKind.TransientSystemUi
        if (isIncomingCall(packageName, cls)) return OverlayWindowKind.IncomingCall
        if (blockedPackage != null && packageName == blockedPackage) {
            return OverlayWindowKind.BlockedApp
        }
        if (isRecentsHost(packageName) && isRecents(cls)) return OverlayWindowKind.Recents
        if (packageName == "com.android.systemui" && isShade(cls)) return OverlayWindowKind.Shade
        if (ForegroundStabilizationPolicy.isTransientForegroundPackage(packageName)) {
            return OverlayWindowKind.Launcher
        }
        if (packageName == "com.android.systemui") return OverlayWindowKind.Unknown
        return OverlayWindowKind.OtherApp
    }

    /**
     * [windows] must be in descending z-order (top first).
     * Skips Gatekeep overlay windows and transient System UI so the app/recents underneath is used.
     */
    fun pickTopRelevantWindow(
        windows: List<OverlayWindowSnapshot>,
        blockedPackage: String?,
        overlayOwnerPackage: String,
    ): OverlayWindowSnapshot? {
        val relevant = windows.filter { window ->
            if (window.isOverlay) return@filter false
            if (window.packageName == overlayOwnerPackage &&
                blockedPackage != overlayOwnerPackage
            ) {
                return@filter false
            }
            val kind = classify(window.packageName, window.className, blockedPackage)
            kind != OverlayWindowKind.TransientSystemUi
        }
        if (relevant.isEmpty()) return null
        val topFocused = relevant.firstOrNull { it.isFocused }
        if (topFocused != null && blockedPackage != null && topFocused.packageName != blockedPackage) {
            val kind = classify(topFocused.packageName, topFocused.className, blockedPackage)
            if (kind == OverlayWindowKind.Launcher ||
                kind == OverlayWindowKind.Recents ||
                kind == OverlayWindowKind.OtherApp ||
                kind == OverlayWindowKind.IncomingCall
            ) {
                return topFocused
            }
        }
        if (blockedPackage != null) {
            relevant.firstOrNull { it.packageName == blockedPackage && it.isFocused }
                ?.let { return it }
        }
        if (blockedPackage != null) {
            relevant.firstOrNull {
                it.packageName == blockedPackage && it.isActive
            }?.let { return it }
        }
        return relevant.firstOrNull { it.isFocused || it.isActive } ?: relevant.first()
    }

    fun isRecents(className: String): Boolean {
        val name = className.lowercase()
        return listOf(
            "recents",
            "overview",
            "recenttasks",
            "taskswitcher",
            "recentsview",
            "recentsactivity",
            "quickstep",
        ).any { name.contains(it) }
    }

    fun isRecentsHost(packageName: String): Boolean =
        packageName == "com.android.systemui" ||
            ForegroundStabilizationPolicy.isTransientForegroundPackage(packageName)

    fun isShade(className: String): Boolean {
        val name = className.lowercase()
        return name.contains("shade") ||
            name.contains("statusbar") ||
            name.contains("notification")
    }

    private fun isTransientSystemUi(packageName: String, className: String): Boolean {
        if (packageName in imePackages) return true
        val name = className.lowercase()
        return name.contains("volume") ||
            name.contains("volumedialog") ||
            name.contains("brightness") ||
            name.contains("toast") ||
            name.contains("globalactions") ||
            name.contains("screenshot") ||
            name.contains("bubble") ||
            name.contains("keyguard") ||
            name.contains("bouncer")
    }

    private fun isIncomingCall(packageName: String, className: String): Boolean {
        val pkg = packageName.lowercase()
        val cls = className.lowercase()
        if (pkg.contains("incallui") || pkg.contains("telecom")) return true
        return cls.contains("incall")
    }
}
