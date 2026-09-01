package com.gatekeep.app.ui.components

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gatekeep.app.R
import kotlinx.coroutines.delay

private fun formatMinuteStepLabel(minutes: Int, positive: Boolean): String {
    val sign = if (positive) "+" else "-"
    return if (minutes >= 60 && minutes % 60 == 0) {
        "$sign${minutes / 60}h"
    } else {
        "$sign${minutes}m"
    }
}

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier.size(40.dp),
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = false
                    runCatching { setImageDrawable(ctx.packageManager.getApplicationIcon(packageName)) }
                }
            },
            update = { view ->
                view.scaleType = ImageView.ScaleType.FIT_CENTER
                view.adjustViewBounds = false
                runCatching { view.setImageDrawable(context.packageManager.getApplicationIcon(packageName)) }
            },
        )
    }
}

private val dayDisplayOrder = listOf(1, 2, 3, 4, 5, 6, 0)

@Composable
private fun dayLetterFor(dayOfWeek: Int): String = when (dayOfWeek) {
    0 -> stringResource(R.string.day_letter_sun)
    1 -> stringResource(R.string.day_letter_mon)
    2 -> stringResource(R.string.day_letter_tue)
    3 -> stringResource(R.string.day_letter_wed)
    4 -> stringResource(R.string.day_letter_thu)
    5 -> stringResource(R.string.day_letter_fri)
    6 -> stringResource(R.string.day_letter_sat)
    else -> stringResource(R.string.day_letter_sun)
}

fun loadAppLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrElse { packageName }



@Composable
fun DayOfWeekSelector(
    selectedDays: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dayDisplayOrder.forEach { dayIndex ->
            val label = dayLetterFor(dayIndex)
            GatekeepFilterChip(
                selected = dayIndex in selectedDays,
                onClick = {
                    val updated = selectedDays.toMutableSet()
                    if (dayIndex in updated) updated.remove(dayIndex) else updated.add(dayIndex)
                    onSelectionChange(updated)
                },
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

@Composable
fun SingleDayOfWeekSelector(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dayDisplayOrder.forEach { dayIndex ->
            val label = dayLetterFor(dayIndex)
            GatekeepFilterChip(
                selected = dayIndex == selectedDay,
                onClick = { onDaySelected(dayIndex) },
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

@Composable
fun IntStepper(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    wheelMin: Int = 1,
    wheelMax: Int = 99,
    wheelEnabled: Boolean = true,
) {
    var showWheel by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldRepeatStepperButton("-") {
                when (value) {
                    null -> Unit
                    1 -> onValueChange(null)
                    else -> onValueChange(value - 1)
                }
            }
            Text(
                text = value?.toString() ?: stringResource(R.string.extension_unlimited),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(enabled = wheelEnabled) {
                        if (wheelEnabled) showWheel = true
                    },
                textAlign = TextAlign.Center,
            )
            HoldRepeatStepperButton("+") {
                onValueChange((value ?: 0) + 1)
            }
        }
    }

    if (showWheel) {
        IntegerOrUnlimitedWheelDialog(
            title = label,
            value = value,
            onDismiss = { showWheel = false },
            onConfirm = { selected ->
                onValueChange(selected)
                showWheel = false
            },
            minValue = wheelMin,
            maxValue = wheelMax,
        )
    }
}

@Composable
fun DurationPickerWithSeconds(
    label: String,
    totalSeconds: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxTotalSeconds: Int? = 3600,
) {
    val isSet = totalSeconds > 0
    val containerColor = if (isSet) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (isSet) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    } else {
        null
    }
    var showCustom by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = border,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HoldRepeatStepperButton("-5m") {
                    onDurationChange((totalSeconds - 300).coerceAtLeast(0))
                }
                HoldRepeatStepperButton("-5s") {
                    onDurationChange((totalSeconds - 5).coerceAtLeast(0))
                }
                Text(
                    formatDurationDisplaySeconds(totalSeconds),
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { showCustom = true },
                )
                HoldRepeatStepperButton("+5s") {
                    onDurationChange(totalSeconds + 5)
                }
                HoldRepeatStepperButton("+5m") {
                    onDurationChange(totalSeconds + 300)
                }
            }
        }
    }

    if (showCustom) {
        RollingDurationDialog(
            initialTotalSeconds = totalSeconds,
            onDismiss = { showCustom = false },
            onConfirm = { seconds ->
                onDurationChange(seconds)
                showCustom = false
            },
            title = stringResource(R.string.custom_duration),
            maxTotalSeconds = maxTotalSeconds,
        )
    }
}

@Composable
fun DurationPicker(
    label: String,
    totalMs: Long,
    onDurationChange: (Long) -> Unit,
    coarseStepMinutes: Int? = null,
    fineStepMinutes: Int = 15,
    minutesOnly: Boolean = false,
    isSet: Boolean = totalMs > 0,
    isError: Boolean = false,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = (totalMs / 1000).toInt()
    var showCustom by remember { mutableStateOf(false) }
    val maxTotalSeconds = if (minutesOnly) 59 * 60 + 59 else null

    fun applyDelta(deltaMinutes: Int) {
        val newSeconds = if (minutesOnly) {
            (totalSeconds + deltaMinutes * 60).coerceIn(0, maxTotalSeconds!!)
        } else {
            (totalSeconds + deltaMinutes * 60).coerceAtLeast(0)
        }
        onDurationChange(newSeconds.toLong() * 1000L)
    }

    val displayText = formatDurationDisplayMilliseconds(totalMs)
    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
        isSet -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        isError -> BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        isSet -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        else -> null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = border,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    isSet -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (coarseStepMinutes != null) {
                    HoldRepeatStepperButton(formatMinuteStepLabel(coarseStepMinutes, positive = false)) {
                        applyDelta(-coarseStepMinutes)
                    }
                }
                HoldRepeatStepperButton(formatMinuteStepLabel(fineStepMinutes, positive = false)) {
                    applyDelta(-fineStepMinutes)
                }
                Text(
                    displayText,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { showCustom = true },
                )
                HoldRepeatStepperButton(formatMinuteStepLabel(fineStepMinutes, positive = true)) {
                    applyDelta(fineStepMinutes)
                }
                if (coarseStepMinutes != null) {
                    HoldRepeatStepperButton(formatMinuteStepLabel(coarseStepMinutes, positive = true)) {
                        applyDelta(coarseStepMinutes)
                    }
                }
            }
        }
    }

    if (showCustom) {
        RollingDurationDialog(
            initialTotalSeconds = totalSeconds,
            onDismiss = { showCustom = false },
            onConfirm = { seconds ->
                onDurationChange(seconds.toLong() * 1000L)
                showCustom = false
            },
            title = stringResource(R.string.custom_duration),
            maxTotalSeconds = maxTotalSeconds,
        )
    }
}

@Composable
fun TimeOfDayPicker(
    label: String,
    minuteOfDay: Int,
    onTimeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    coarseStepMinutes: Int? = null,
    fineStepMinutes: Int = 5,
) {
    val hours = minuteOfDay / 60
    val minutes = minuteOfDay % 60
    var showCustom by remember { mutableStateOf(false) }

    fun applyDelta(delta: Int) {
        onTimeChange((minuteOfDay + delta).coerceIn(0, 24 * 60 - 1))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coarseStepMinutes != null) {
                HoldRepeatStepperButton(formatMinuteStepLabel(coarseStepMinutes, positive = false)) {
                    applyDelta(-coarseStepMinutes)
                }
            }
            HoldRepeatStepperButton(formatMinuteStepLabel(fineStepMinutes, positive = false)) {
                applyDelta(-fineStepMinutes)
            }
            Text(
                "%02d:%02d".format(hours, minutes),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { showCustom = true },
            )
            HoldRepeatStepperButton(formatMinuteStepLabel(fineStepMinutes, positive = true)) {
                applyDelta(fineStepMinutes)
            }
            if (coarseStepMinutes != null) {
                HoldRepeatStepperButton(formatMinuteStepLabel(coarseStepMinutes, positive = true)) {
                    applyDelta(coarseStepMinutes)
                }
            }
        }
    }

    if (showCustom) {
        TwentyFourHourClockDialog(
            initialMinuteOfDay = minuteOfDay,
            onDismiss = { showCustom = false },
            onConfirm = { minute ->
                onTimeChange(minute)
                showCustom = false
            },
            title = stringResource(R.string.custom_time),
        )
    }
}

@Composable
private fun HoldRepeatStepperButton(text: String, onStep: () -> Unit) {
    val currentStep by rememberUpdatedState(onStep)
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(held) {
        if (!held) return@LaunchedEffect
        delay(400)
        while (held) {
            currentStep()
            delay(80)
        }
    }
    Surface(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    val pressStart = System.currentTimeMillis()
                    held = true
                    tryAwaitRelease()
                    held = false
                    if (System.currentTimeMillis() - pressStart < 400) {
                        currentStep()
                    }
                },
            )
        },
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
