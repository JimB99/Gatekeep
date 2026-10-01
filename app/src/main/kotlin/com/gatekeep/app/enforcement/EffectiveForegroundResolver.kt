package com.gatekeep.app.enforcement

/**
 * Chooses which package counts as foreground for overlay presentation.
 * UsageStats may lag behind accessibility when returning from launcher/drawer.
 */
object EffectiveForegroundResolver {

    fun resolve(
        accessibilityForeground: String?,
        usageStatsForeground: String?,
        blockedPackage: String?,
        presentation: BlockPresentation,
    ): String? {
        if (accessibilityForeground == null) {
            return usageStatsForeground
        }
        if (blockedPackage != null &&
            accessibilityForeground == blockedPackage &&
            presentation is BlockPresentation.Visible
        ) {
            if (usageStatsForeground != null && usageStatsForeground != blockedPackage) {
                return usageStatsForeground
            }
        }
        return accessibilityForeground
    }
}
