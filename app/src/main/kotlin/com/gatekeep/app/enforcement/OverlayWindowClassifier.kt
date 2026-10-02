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
        if (isRecents(cls)) return OverlayWindowKind.Recents
        if (isShade(cls)) return OverlayWindowKind.Shade
        if (isIncomingCall(packageName, cls)) return OverlayWindowKind.IncomingCall
        if (ForegroundStabilizationPolicy.isTransientForegroundPackage(packageName)) {
            return OverlayWindowKind.Launcher
        }
        if (blockedPackage != null && packageName == blockedPackage) {
            return OverlayWindowKind.BlockedApp
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
        for (window in windows) {
            if (window.isOverlay) continue
            if (window.packageName == overlayOwnerPackage) continue
            val kind = classify(window.packageName, window.className, blockedPackage)
            if (kind == OverlayWindowKind.TransientSystemUi) continue
            return window
        }
        return null
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
            name.contains("bubble")
    }

    private fun isIncomingCall(packageName: String, className: String): Boolean {
        val pkg = packageName.lowercase()
        val cls = className.lowercase()
        if (pkg.contains("incallui") || pkg.contains("telecom")) return true
        return cls.contains("incall")
    }
}
