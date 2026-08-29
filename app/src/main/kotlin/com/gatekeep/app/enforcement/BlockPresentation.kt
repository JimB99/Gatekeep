package com.gatekeep.app.enforcement

sealed interface BlockPresentation {
    data object None : BlockPresentation

    data class Visible(
        val packageName: String,
        val generation: Long,
    ) : BlockPresentation

    data class HiddenForOtherApp(
        val packageName: String,
        val generation: Long,
    ) : BlockPresentation
}

data class BlockPresentationState(
    val presentation: BlockPresentation = BlockPresentation.None,
    val generation: Long = 0L,
)

data class EvaluationToken(
    val packageName: String,
    val blockGeneration: Long,
)
