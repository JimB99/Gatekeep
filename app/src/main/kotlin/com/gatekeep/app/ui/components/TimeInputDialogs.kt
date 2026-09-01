package com.gatekeep.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatekeep.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwentyFourHourClockDialog(
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (minuteOfDay: Int) -> Unit,
    title: String,
) {
    val initialHour = (initialMinuteOfDay / 60).coerceIn(0, 23)
    val initialMinute = (initialMinuteOfDay % 60).coerceIn(0, 59)
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TimePicker(state = timeState)
        },
        confirmButton = {
            TextButton(onClick = {
                val minuteOfDay = timeState.hour * 60 + timeState.minute
                onConfirm(minuteOfDay.coerceIn(0, 24 * 60 - 1))
            }) {
                Text(stringResource(R.string.set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun RollingDurationDialog(
    initialTotalSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (totalSeconds: Int) -> Unit,
    title: String,
    maxTotalSeconds: Int? = null,
) {
    var input by remember {
        mutableStateOf(RollingDurationInput.fromTotalSeconds(initialTotalSeconds))
    }
    val exceedsRollingRange = initialTotalSeconds > RollingDurationInput.MAX_TOTAL_SECONDS
    val validation = input.validate(maxTotalSeconds)
    val errorMessage = when (validation) {
        DurationValidationResult.Valid -> null
        DurationValidationResult.InvalidMinuteOrSecond ->
            stringResource(R.string.duration_invalid_minute_second)
        is DurationValidationResult.ExceedsMaximum ->
            stringResource(R.string.duration_exceeds_maximum, formatMaxDurationLabel(validation.maxTotalSeconds))
    }
    val rollingRangeMessage = if (exceedsRollingRange) {
        stringResource(
            R.string.duration_exceeds_rolling_maximum,
            RollingDurationInput.fromTotalSeconds(RollingDurationInput.MAX_TOTAL_SECONDS).formatted(),
        )
    } else {
        null
    }

    val formattedDisplay = input.formatted()
    val displayA11y = stringResource(R.string.duration_keypad_display_a11y, formattedDisplay)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = formattedDisplay,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = displayA11y },
                )
                if (rollingRangeMessage != null) {
                    Text(
                        text = rollingRangeMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                DurationKeypad(
                    onDigit = { digit -> input = input.insertDigit(digit) },
                    onDoubleZero = { input = input.insertDoubleZero() },
                    onBackspace = { input = input.backspace() },
                    onClear = { input = input.clear() },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input.totalSeconds()) },
                enabled = validation is DurationValidationResult.Valid,
            ) {
                Text(stringResource(R.string.set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DurationKeypad(
    onDigit: (Int) -> Unit,
    onDoubleZero: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { label ->
                    val onClick = when (label) {
                        "00" -> onDoubleZero
                        "⌫" -> onBackspace
                        else -> { { onDigit(label.toInt()) } }
                    }
                    val a11yLabel = when (label) {
                        "00" -> stringResource(R.string.duration_keypad_double_zero_a11y)
                        "⌫" -> stringResource(R.string.duration_keypad_backspace_a11y)
                        else -> stringResource(R.string.duration_keypad_digit_a11y, label)
                    }
                    DurationKeypadButton(
                        label = label,
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        contentDescription = a11yLabel,
                    )
                }
            }
        }
        DurationKeypadButton(
            label = stringResource(R.string.duration_keypad_clear),
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = stringResource(R.string.duration_keypad_clear_a11y),
        )
    }
}

@Composable
private fun DurationKeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun formatMaxDurationLabel(maxTotalSeconds: Int): String {
    val input = RollingDurationInput.fromTotalSeconds(maxTotalSeconds)
    return input.formatted()
}
