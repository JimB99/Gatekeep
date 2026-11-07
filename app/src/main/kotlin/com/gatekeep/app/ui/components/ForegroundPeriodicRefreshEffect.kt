package com.gatekeep.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Invokes [onRefresh] every [intervalMs] while this destination is in [Lifecycle.State.RESUMED].
 */
@Composable
fun ForegroundPeriodicRefreshEffect(
    enabled: Boolean = true,
    intervalMs: Long = DEFAULT_INTERVAL_MS,
    onRefresh: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onRefresh)
    val interval = ForegroundPeriodicRefreshRules.coercedIntervalMs(intervalMs)
    LaunchedEffect(lifecycleOwner, enabled, interval) {
        if (!ForegroundPeriodicRefreshRules.shouldSchedule(enabled)) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                refresh()
                delay(interval)
            }
        }
    }
}

const val DEFAULT_INTERVAL_MS = ForegroundPeriodicRefreshRules.DEFAULT_INTERVAL_MS
