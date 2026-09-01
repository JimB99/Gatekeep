package com.gatekeep.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun IntegerOrUnlimitedWheelDialog(
    title: String,
    value: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    minValue: Int = 1,
    maxValue: Int = 99,
) {
    val unlimitedLabel = stringResource(R.string.extension_unlimited)
    val options = remember(minValue, maxValue) {
        listOf<Int?>(null) + (minValue..maxValue).map { it }
    }
    val initialIndex = options.indexOf(value).let { if (it >= 0) it else 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0))
    var selectedIndex by remember { mutableIntStateOf(initialIndex.coerceAtLeast(0)) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index in options.indices) {
                    selectedIndex = index
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                ) {
                    items(options.size) { index ->
                        val option = options[index]
                        val isSelected = index == selectedIndex
                        Text(
                            text = option?.toString() ?: unlimitedLabel,
                            style = if (isSelected) {
                                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIndex = index
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options[selectedIndex]) }) {
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
