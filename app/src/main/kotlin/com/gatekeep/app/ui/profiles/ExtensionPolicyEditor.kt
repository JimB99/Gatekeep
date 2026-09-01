@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatekeep.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.gatekeep.app.ui.components.GatekeepFilterChipRow
import com.gatekeep.app.ui.components.IntStepper
import com.gatekeep.domain.model.ExtensionPolicy
import com.gatekeep.domain.model.ExtensionSurfaceMode

private val PRESET_MINUTES = listOf(1, 5, 15, 60)
private const val MAX_EXTENSIONS_WHEEL_MAX = 99

data class ExtensionPolicyDraft(
    val selectedPresets: Set<Int>,
    val customEnabled: Boolean,
    val customMinutesText: String,
    val maxPerDay: Int?,
    val maxConsecutive: Int?,
    val showNoLimitToday: Boolean,
    val surfaceMode: ExtensionSurfaceMode = ExtensionSurfaceMode.both,
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
        customMinutes = customMinutesText.toIntOrNull()?.takeIf { it in 1..999 },
        customEnabled = customEnabled,
        surfaceMode = surfaceMode,
    )

    companion object {
        fun fromPolicy(policy: ExtensionPolicy): ExtensionPolicyDraft {
            val legacyPresets = setOf(1, 5, 10, 15, 60)
            val presets = policy.optionMinutes.filter { it in legacyPresets }.toSet()
            val storedCustom = policy.customMinutes
                ?: policy.optionMinutes.filter { it !in legacyPresets }.firstOrNull()
            val migratedPresets = when {
                presets.isNotEmpty() -> presets
                policy.optionMinutes.contains(10) -> presets + 10
                else -> setOf(1, 5, 15)
            }.map { if (it == 10) 15 else it }.toSet()
            return ExtensionPolicyDraft(
                selectedPresets = migratedPresets.ifEmpty { setOf(1, 5, 15) },
                customEnabled = policy.customEnabled,
                customMinutesText = storedCustom?.toString() ?: "",
                maxPerDay = policy.maxExtensionsPerDay,
                maxConsecutive = policy.maxConsecutiveExtensions,
                showNoLimitToday = policy.showNoLimitToday,
                surfaceMode = policy.surfaceMode,
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
        GatekeepFilterChipRow {
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
                    label = { Text(formatPresetLabel(minutes), maxLines = 2) },
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
                        maxLines = 2,
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
        Text(
            stringResource(R.string.extension_overlay_only_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GatekeepFilterChip(
            selected = draft.showNoLimitToday,
            onClick = {
                onDraftChange(draft.copy(showNoLimitToday = !draft.showNoLimitToday))
            },
            label = {
                Text(
                    stringResource(R.string.show_no_limit_today),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        val preview = draft.resolvedOptionMinutes().joinToString(", ") { formatPresetLabel(it) }
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
            wheelMax = MAX_EXTENSIONS_WHEEL_MAX,
        )
        IntStepper(
            label = stringResource(R.string.max_consecutive_extensions),
            value = draft.maxConsecutive,
            onValueChange = { newConsecutive ->
                val bumpedDaily = when {
                    newConsecutive != null && draft.maxPerDay != null && newConsecutive > draft.maxPerDay ->
                        newConsecutive
                    else -> draft.maxPerDay
                }
                val capped = when {
                    newConsecutive == null -> null
                    bumpedDaily != null -> minOf(newConsecutive, bumpedDaily)
                    else -> newConsecutive
                }
                onDraftChange(draft.copy(maxPerDay = bumpedDaily, maxConsecutive = capped))
            },
            wheelMax = MAX_EXTENSIONS_WHEEL_MAX,
        )
    }
}

private fun formatPresetLabel(minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"
