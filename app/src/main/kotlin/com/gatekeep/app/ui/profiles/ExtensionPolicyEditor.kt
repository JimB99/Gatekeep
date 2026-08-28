package com.gatekeep.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.components.IntStepper
import com.gatekeep.domain.model.ExtensionPolicy

private val PRESET_MINUTES = listOf(1, 5, 10, 15)

data class ExtensionPolicyDraft(
    val selectedPresets: Set<Int>,
    val customEnabled: Boolean,
    val customMinutesText: String,
    val maxPerDay: Int?,
    val maxConsecutive: Int?,
    val showNoLimitToday: Boolean,
) {
    fun resolvedOptionMinutes(): List<Int> {
        val presets = selectedPresets.sorted()
        val custom = customMinutesText.toIntOrNull()
            ?.takeIf { it in 1..999 && customEnabled }
        return (presets + listOfNotNull(custom)).distinct().sorted().ifEmpty { listOf(5) }
    }

    fun toPolicy(): ExtensionPolicy = ExtensionPolicy(
        optionMinutes = resolvedOptionMinutes(),
        maxExtensionsPerDay = maxPerDay,
        maxConsecutiveExtensions = maxConsecutive?.let { consecutive ->
            maxPerDay?.let { daily -> minOf(consecutive, daily) } ?: consecutive
        },
        showNoLimitToday = showNoLimitToday,
    )

    companion object {
        fun fromPolicy(policy: ExtensionPolicy): ExtensionPolicyDraft {
            val presets = policy.optionMinutes.filter { it in PRESET_MINUTES }.toSet()
            val customValues = policy.optionMinutes.filter { it !in PRESET_MINUTES }
            val custom = customValues.firstOrNull()
            return ExtensionPolicyDraft(
                selectedPresets = presets.ifEmpty { setOf(1, 5, 10) },
                customEnabled = custom != null,
                customMinutesText = custom?.toString() ?: "",
                maxPerDay = policy.maxExtensionsPerDay,
                maxConsecutive = policy.maxConsecutiveExtensions,
                showNoLimitToday = policy.showNoLimitToday,
            )
        }
    }
}

@Composable
fun ExtensionPolicyEditor(
    draft: ExtensionPolicyDraft,
    onDraftChange: (ExtensionPolicyDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.extension_minutes_label), style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PRESET_MINUTES.forEach { minutes ->
                GatekeepFilterChip(
                    selected = minutes in draft.selectedPresets,
                    onClick = {
                        val updated = if (minutes in draft.selectedPresets) {
                            draft.selectedPresets - minutes
                        } else {
                            draft.selectedPresets + minutes
                        }
                        val finalPresets = updated.ifEmpty { setOf(minutes) }
                        onDraftChange(draft.copy(selectedPresets = finalPresets))
                    },
                    label = { Text("${minutes}m", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            GatekeepFilterChip(
                selected = draft.customEnabled,
                onClick = {
                    onDraftChange(draft.copy(customEnabled = !draft.customEnabled))
                },
                label = {
                    Text(
                        stringResource(R.string.extension_custom_short),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        if (draft.customEnabled) {
            OutlinedTextField(
                value = draft.customMinutesText,
                onValueChange = { value ->
                    onDraftChange(
                        draft.copy(customMinutesText = value.filter { it.isDigit() }.take(3)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.extension_custom_minutes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        val preview = draft.resolvedOptionMinutes().joinToString(", ") { "${it}m" }
        Text(
            stringResource(R.string.extension_overlay_preview, preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IntStepper(
            label = stringResource(R.string.max_extensions_per_day),
            value = draft.maxPerDay,
            onValueChange = { newMaxPerDay ->
                val cappedConsecutive = draft.maxConsecutive?.let { consecutive ->
                    newMaxPerDay?.let { daily -> minOf(consecutive, daily) } ?: consecutive
                }
                onDraftChange(draft.copy(maxPerDay = newMaxPerDay, maxConsecutive = cappedConsecutive))
            },
        )
        IntStepper(
            label = stringResource(R.string.max_consecutive_extensions),
            value = draft.maxConsecutive,
            onValueChange = { newConsecutive ->
                val capped = when {
                    newConsecutive == null -> null
                    draft.maxPerDay != null -> minOf(newConsecutive, draft.maxPerDay)
                    else -> newConsecutive
                }
                onDraftChange(draft.copy(maxConsecutive = capped))
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = draft.showNoLimitToday,
                onCheckedChange = { onDraftChange(draft.copy(showNoLimitToday = it)) },
            )
            Text(
                stringResource(R.string.show_no_limit_today),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
