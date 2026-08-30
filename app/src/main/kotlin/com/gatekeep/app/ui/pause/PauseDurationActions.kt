package com.gatekeep.app.ui.pause

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.SaveChangesButton

data class CustomDurationState(
    val minutesText: String = "",
    val expanded: Boolean = false,
)

val CustomDurationStateSaver = listSaver<CustomDurationState, Any>(
    save = { listOf(it.minutesText, it.expanded) },
    restore = {
        CustomDurationState(
            minutesText = it[0] as String,
            expanded = it[1] as Boolean,
        )
    },
)

@Composable
fun DurationActionGrid(
    activeChoice: DurationChoice?,
    draftChoice: DurationChoice?,
    customState: CustomDurationState,
    onCustomStateChange: (CustomDurationState) -> Unit,
    onDraftSelect: (DurationChoice) -> Unit,
    onApply: () -> Unit,
    onUntilDate: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DurationChoiceButton(
                label = stringResource(R.string.duration_5_min),
                isActive = activeChoice is DurationChoice.PresetMinutes && activeChoice.minutes == 5,
                isDraft = draftChoice is DurationChoice.PresetMinutes && draftChoice.minutes == 5,
                onClick = { onDraftSelect(DurationChoice.PresetMinutes(5)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            DurationChoiceButton(
                label = stringResource(R.string.duration_15_min),
                isActive = activeChoice is DurationChoice.PresetMinutes && activeChoice.minutes == 15,
                isDraft = draftChoice is DurationChoice.PresetMinutes && draftChoice.minutes == 15,
                onClick = { onDraftSelect(DurationChoice.PresetMinutes(15)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            DurationChoiceButton(
                label = stringResource(R.string.duration_60_min),
                isActive = activeChoice is DurationChoice.PresetMinutes && activeChoice.minutes == 60,
                isDraft = draftChoice is DurationChoice.PresetMinutes && draftChoice.minutes == 60,
                onClick = { onDraftSelect(DurationChoice.PresetMinutes(60)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DurationChoiceButton(
                label = stringResource(R.string.duration_custom),
                isActive = activeChoice is DurationChoice.CustomMinutes,
                isDraft = draftChoice is DurationChoice.CustomMinutes,
                onClick = {
                    onCustomStateChange(customState.copy(expanded = !customState.expanded))
                    customState.minutesText.toIntOrNull()?.takeIf { it in 1..999 }?.let { minutes ->
                        onDraftSelect(DurationChoice.CustomMinutes(minutes))
                    }
                },
                enabled = enabled,
            )
            if (customState.expanded) {
                OutlinedTextField(
                    value = customState.minutesText,
                    onValueChange = { value ->
                        val text = value.filter { it.isDigit() }.take(3)
                        onCustomStateChange(customState.copy(minutesText = text))
                        text.toIntOrNull()?.takeIf { it in 1..999 }?.let { minutes ->
                            onDraftSelect(DurationChoice.CustomMinutes(minutes))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 80.dp),
                    label = { Text(stringResource(R.string.extension_custom_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = enabled,
                )
            }
        }
        DurationChoiceButton(
            label = stringResource(R.string.duration_today),
            isActive = activeChoice is DurationChoice.Today,
            isDraft = draftChoice is DurationChoice.Today,
            onClick = { onDraftSelect(DurationChoice.Today) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        DurationChoiceButton(
            label = stringResource(R.string.duration_until_date),
            isActive = activeChoice is DurationChoice.UntilDateTime,
            isDraft = draftChoice is DurationChoice.UntilDateTime,
            onClick = onUntilDate,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        SaveChangesButton(
            visible = draftChoice != null,
            onClick = onApply,
            label = stringResource(R.string.pause_apply),
            enabled = enabled,
        )
    }
}

@Composable
private fun DurationChoiceButton(
    label: String,
    isActive: Boolean,
    isDraft: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = when {
        isActive -> ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
        isDraft -> ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        else -> ButtonDefaults.outlinedButtonColors()
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = colors,
        border = BorderStroke(
            1.dp,
            when {
                isActive -> MaterialTheme.colorScheme.primary
                isDraft -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Text(label)
    }
}

@Composable
fun PauseResetBlock(
    onReset: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.pause_reset_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.pause_reset_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onReset,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pause_reset_action))
        }
    }
}

@Composable
fun PauseSectionHeader(title: String, help: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActiveUntilBanner(
    label: String,
    onEndEarly: () -> Unit,
    showEndEarly: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        if (showEndEarly) {
            androidx.compose.material3.TextButton(onClick = onEndEarly) {
                Text(stringResource(R.string.end_early))
            }
        }
    }
}
