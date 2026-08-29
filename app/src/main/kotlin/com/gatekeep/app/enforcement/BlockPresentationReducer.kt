package com.gatekeep.app.enforcement

object BlockPresentationReducer {

    fun blockedPackage(state: BlockPresentationState): String? = when (val p = state.presentation) {
        is BlockPresentation.Visible -> p.packageName
        is BlockPresentation.HiddenForOtherApp -> p.packageName
        BlockPresentation.None -> null
    }

    fun isBlockingActive(state: BlockPresentationState): Boolean =
        state.presentation !is BlockPresentation.None

    fun onBlockEntered(state: BlockPresentationState, packageName: String): BlockPresentationState {
        val nextGen = state.generation + 1
        return BlockPresentationState(
            presentation = BlockPresentation.Visible(packageName, nextGen),
            generation = nextGen,
        )
    }

    fun onHideForOtherApp(state: BlockPresentationState): BlockPresentationState {
        val presentation = state.presentation
        if (presentation !is BlockPresentation.Visible) return state
        return state.copy(
            presentation = BlockPresentation.HiddenForOtherApp(
                packageName = presentation.packageName,
                generation = presentation.generation,
            ),
        )
    }

    fun onReturnToBlockedApp(state: BlockPresentationState, packageName: String): BlockPresentationState {
        val presentation = state.presentation
        if (presentation !is BlockPresentation.HiddenForOtherApp ||
            presentation.packageName != packageName
        ) {
            return state
        }
        return state.copy(
            presentation = BlockPresentation.Visible(
                packageName = packageName,
                generation = presentation.generation,
            ),
        )
    }

    fun onBlockCleared(state: BlockPresentationState): BlockPresentationState {
        val nextGen = state.generation + 1
        return BlockPresentationState(
            presentation = BlockPresentation.None,
            generation = nextGen,
        )
    }

    fun isGatekeepActivityWindow(className: String?): Boolean =
        className?.startsWith("com.gatekeep.app.") == true && className.endsWith("Activity")

    fun shouldIgnoreGatekeepForegroundEvent(
        state: BlockPresentationState,
        overlayVisible: Boolean,
        windowClassName: String?,
    ): Boolean {
        if (!overlayVisible || !isBlockingActive(state)) return false
        if (isGatekeepActivityWindow(windowClassName)) return false
        return true
    }

    fun shouldApplyAllowedClear(
        state: BlockPresentationState,
        token: EvaluationToken,
        currentForegroundPackage: String?,
    ): Boolean {
        if (currentForegroundPackage != token.packageName) return false
        return when (val presentation = state.presentation) {
            BlockPresentation.None -> true
            is BlockPresentation.Visible -> {
                presentation.packageName == token.packageName &&
                    token.blockGeneration >= presentation.generation
            }
            is BlockPresentation.HiddenForOtherApp -> {
                presentation.packageName == token.packageName &&
                    token.blockGeneration >= presentation.generation
            }
        }
    }
}
