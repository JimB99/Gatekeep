package com.gatekeep.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gatekeep.app.data.ChartBucket
import com.gatekeep.app.util.formatChartAxisTick
import com.gatekeep.domain.UsageBucketAggregator

@Composable
fun StatsBarChart(
    buckets: List<ChartBucket>,
    scaleMs: Long,
    modifier: Modifier = Modifier,
    chartHeightDp: Dp = 120.dp,
    enableHorizontalScroll: Boolean = false,
    labelInterval: Int = 5,
    rotateLabels: Boolean = false,
) {
    if (buckets.isEmpty()) return

    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxUsageMs = buckets.maxOf { it.usageMs }
    val effectiveScaleMs = remember(scaleMs, maxUsageMs) {
        val base = scaleMs.coerceAtLeast(UsageBucketAggregator.snapToTickStep(1))
        val usageScale = if (maxUsageMs > 0) UsageBucketAggregator.snapToTickStep(maxUsageMs) else base
        maxOf(base, usageScale)
    }
    val tickValues = remember(effectiveScaleMs, maxUsageMs) {
        UsageBucketAggregator.computeChartAxisTicks(effectiveScaleMs, maxUsageMs)
    }
    val tickLabels = remember(tickValues) { tickValues.map(::formatChartAxisTick) }
    val scale = tickValues.maxOrNull()?.toFloat()?.coerceAtLeast(1f)
        ?: effectiveScaleMs.coerceAtLeast(1L).toFloat()
    val scrollState = rememberScrollState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val axisWidth = 40.dp
    val chartGap = 8.dp
    val barGap = 4.dp
    val availableChartWidth = screenWidthDp - axisWidth - chartGap - 32.dp
    val computedBarWidth = if (enableHorizontalScroll) {
        when {
            buckets.size <= 12 -> 24.dp
            buckets.size <= 31 -> 12.dp
            else -> 24.dp
        }
    } else {
        val totalGap = barGap * (buckets.size - 1).coerceAtLeast(0)
        ((availableChartWidth - totalGap) / buckets.size.coerceAtLeast(1)).coerceIn(3.dp, 32.dp)
    }
    val chartContentWidth = if (enableHorizontalScroll) {
        computedBarWidth * buckets.size + barGap * (buckets.size - 1).coerceAtLeast(0)
    } else {
        availableChartWidth
    }
    val labelRowHeight = if (rotateLabels) 52.dp else 28.dp
    val labelStyle = if (rotateLabels) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelSmall
    }
    val barSlotWidthDp = if (enableHorizontalScroll) {
        computedBarWidth + barGap
    } else {
        chartContentWidth / buckets.size.coerceAtLeast(1)
    }

    Row(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(axisWidth)
                .height(chartHeightDp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            tickLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = axisLabelColor,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.width(chartGap))

        val chartModifier = Modifier
            .weight(1f)
            .then(if (enableHorizontalScroll) Modifier.horizontalScroll(scrollState) else Modifier)

        Column(modifier = chartModifier) {
            Canvas(
                modifier = Modifier
                    .width(chartContentWidth)
                    .height(chartHeightDp)
                    .clipToBounds(),
            ) {
                val chartHeight = size.height
                val barSlotWidth = size.width / buckets.size.coerceAtLeast(1)
                val tickFractions = tickValues.map { tick -> (tick.toFloat() / scale).coerceIn(0f, 1f) }
                tickFractions.forEach { fraction ->
                    val y = chartHeight * (1f - fraction)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }
                buckets.forEachIndexed { index, bucket ->
                    val fraction = (bucket.usageMs.toFloat() / scale).coerceIn(0f, 1f)
                    val barHeight = (chartHeight * fraction).coerceAtLeast(if (bucket.usageMs > 0) 2f else 0f)
                    val left = index * barSlotWidth + barSlotWidth * 0.15f
                    val barWidth = barSlotWidth * 0.7f
                    drawRect(
                        color = barColor,
                        topLeft = Offset(left, chartHeight - barHeight),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .width(chartContentWidth)
                    .height(labelRowHeight)
                    .padding(top = 4.dp),
            ) {
                buckets.forEachIndexed { index, bucket ->
                    val showLabel = when {
                        !enableHorizontalScroll -> true
                        index == 0 || index == buckets.lastIndex -> true
                        labelInterval > 0 && (index + 1) % labelInterval == 0 -> true
                        else -> false
                    }
                    val slotModifier = if (enableHorizontalScroll) {
                        Modifier.width(barSlotWidthDp)
                    } else {
                        Modifier.weight(1f)
                    }
                    Column(
                        modifier = slotModifier.padding(horizontal = 1.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        if (showLabel) {
                            Text(
                                text = bucket.label,
                                style = labelStyle,
                                color = axisLabelColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                softWrap = false,
                                modifier = if (rotateLabels) {
                                    Modifier.graphicsLayer {
                                        rotationZ = -45f
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            bucket.subLabel?.let { sub ->
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = axisLabelColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
