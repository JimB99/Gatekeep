package com.gatekeep.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

object WheelCenterSelector {
    data class Item(val index: Int, val offset: Int, val size: Int)

    fun selectedIndex(items: List<Item>, viewportStart: Int, viewportEnd: Int): Int? {
        if (items.isEmpty()) return null
        val center = (viewportStart + viewportEnd) / 2
        return items.minByOrNull { item ->
            kotlin.math.abs(item.offset + item.size / 2 - center)
        }?.index
    }
}

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
    val itemHeight = 48.dp
    val pickerHeight = 192.dp
    val verticalPadding = (pickerHeight - itemHeight) / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0))
    var selectedIndex by remember { mutableIntStateOf(initialIndex.coerceAtLeast(0)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            WheelCenterSelector.selectedIndex(
                items = info.visibleItemsInfo.map { item ->
                    WheelCenterSelector.Item(index = item.index, offset = item.offset, size = item.size)
                },
                viewportStart = info.viewportStartOffset,
                viewportEnd = info.viewportEndOffset,
            )
        }
            .distinctUntilChanged()
            .collect { index ->
                if (index != null && index in options.indices) {
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
                    .height(pickerHeight),
                contentAlignment = Alignment.Center,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(pickerHeight),
                    contentPadding = PaddingValues(vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                ) {
                    items(options.size) { index ->
                        val option = options[index]
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable {
                                    selectedIndex = index
                                    scope.launch { listState.animateScrollToItem(index) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
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
                            )
                        }
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
