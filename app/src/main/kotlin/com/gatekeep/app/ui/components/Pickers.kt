package com.gatekeep.app.ui.components

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier.size(40.dp),
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                runCatching { setImageDrawable(ctx.packageManager.getApplicationIcon(packageName)) }
            }
        },
        update = { view ->
            runCatching { view.setImageDrawable(context.packageManager.getApplicationIcon(packageName)) }
        },
    )
}

fun loadAppLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrElse { packageName }

private val DAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun DayOfWeekSelector(
    selectedDays: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DAY_LABELS.forEachIndexed { index, label ->
            FilterChip(
                selected = index in selectedDays,
                onClick = {
                    val updated = selectedDays.toMutableSet()
                    if (index in updated) updated.remove(index) else updated.add(index)
                    onSelectionChange(updated)
                },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun DurationPicker(
    label: String,
    totalMs: Long,
    onDurationChange: (Long) -> Unit,
    minuteStep: Int = 1,
    modifier: Modifier = Modifier,
) {
    val totalMinutes = (totalMs / 60_000).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    var showCustom by remember { mutableStateOf(false) }
    var customHours by remember { mutableIntStateOf(hours) }
    var customMinutes by remember { mutableIntStateOf(minutes) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("-") {
                val newMin = (totalMinutes - minuteStep).coerceAtLeast(0)
                onDurationChange(newMin * 60_000L)
            }
            Text(
                "${hours}h ${minutes}m",
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        customHours = hours
                        customMinutes = minutes
                        showCustom = true
                    },
            )
            StepperButton("+") {
                onDurationChange((totalMinutes + minuteStep) * 60_000L)
            }
        }
    }

    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Custom duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customHours.toString(),
                        onValueChange = { customHours = it.toIntOrNull()?.coerceAtLeast(0) ?: 0 },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = customMinutes.toString(),
                        onValueChange = { customMinutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDurationChange((customHours * 60L + customMinutes) * 60_000L)
                    showCustom = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun TimeOfDayPicker(
    label: String,
    minuteOfDay: Int,
    onTimeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = minuteOfDay / 60
    val minutes = minuteOfDay % 60
    var showCustom by remember { mutableStateOf(false) }
    var customHours by remember { mutableIntStateOf(hours) }
    var customMinutes by remember { mutableIntStateOf(minutes) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("-5m") {
                onTimeChange((minuteOfDay - 5).coerceAtLeast(0))
            }
            Text(
                "%02d:%02d".format(hours, minutes),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        customHours = hours
                        customMinutes = minutes
                        showCustom = true
                    },
            )
            StepperButton("+5m") {
                onTimeChange((minuteOfDay + 5).coerceAtMost(24 * 60 - 1))
            }
        }
    }

    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Custom time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customHours.toString(),
                        onValueChange = { customHours = it.toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                        label = { Text("Hour (0–23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = customMinutes.toString(),
                        onValueChange = { customMinutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(customHours * 60 + customMinutes)
                    showCustom = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick) { Text(text) }
}
