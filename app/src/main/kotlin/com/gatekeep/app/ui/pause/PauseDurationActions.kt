package com.gatekeep.app.ui.pause

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationActionGrid(
    onFiveMin: () -> Unit,
    onFifteenMin: () -> Unit,
    onSixtyMin: () -> Unit,
    onCustom: () -> Unit,
    onToday: () -> Unit,
    onUntilDate: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onFiveMin, enabled = enabled) {
                Text(stringResource(R.string.duration_5_min))
            }
            OutlinedButton(onClick = onFifteenMin, enabled = enabled) {
                Text(stringResource(R.string.duration_15_min))
            }
            OutlinedButton(onClick = onSixtyMin, enabled = enabled) {
                Text(stringResource(R.string.duration_60_min))
            }
            OutlinedButton(onClick = onCustom, enabled = enabled) {
                Text(stringResource(R.string.duration_custom))
            }
        }
        Button(
            onClick = onToday,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.duration_today))
        }
        Button(
            onClick = onUntilDate,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.duration_until_date))
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
