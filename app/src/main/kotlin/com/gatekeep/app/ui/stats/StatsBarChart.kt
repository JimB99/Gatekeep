package com.gatekeep.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
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
    labelInterval: Int = 1,
    oneBasedLabelInterval: Boolean = false,
    rotateLabels: Boolean = false,
) {
    if (buckets.isEmpty()) return

    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val zeroLabelColor = axisLabelColor.copy(alpha = 0.45f)
    val labelStyle = if (rotateLabels) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelSmall
    }
    val maxUsageMs = buckets.maxOf { it.usageMs }
    val effectiveScaleMs = remember(scaleMs, maxUsageMs) {
        val base = scaleMs.coerceAtLeast(UsageBucketAggregator.snapToTickStep(1))
        val usageScale = if (maxUsageMs > 0) UsageBucketAggregator.snapToTickStep(maxUsageMs) else base
        maxOf(base, usageScale)
    }
    val tickValues = remember(effectiveScaleMs, maxUsageMs) {
        UsageBucketAggregator.computeChartAxisTicks(effectiveScaleMs, maxUsageMs)
    }
    val showZeroLabel = maxUsageMs > 0
    val scale = tickValues.maxOrNull()?.toFloat()?.coerceAtLeast(1f)
        ?: effectiveScaleMs.coerceAtLeast(1L).toFloat()
    val tickFractions = remember(tickValues, scale) {
        tickValues.map { tick -> (tick.toFloat() / scale).coerceIn(0f, 1f) }
    }
    val axisLabels = remember(tickValues, showZeroLabel) {
        tickValues.map(::formatChartAxisTick) + if (showZeroLabel) listOf("0") else emptyList()
    }
    val axisFractions = remember(tickFractions, showZeroLabel) {
        tickFractions + if (showZeroLabel) listOf(0f) else emptyList()
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val axisWidth = remember(axisLabels, labelStyle) {
        val maxLabelWidthPx = axisLabels.maxOfOrNull { label ->
            textMeasurer.measure(label, style = labelStyle).size.width
        } ?: 0
        with(density) { maxLabelWidthPx.toDp() } + 4.dp
    }

    val chartGap = 4.dp
    val hasSubLabels = buckets.any { it.subLabel != null }
    val labelRowHeight = when {
        rotateLabels -> 56.dp
        hasSubLabels -> 44.dp
        else -> 28.dp
    }

    fun shouldShowLabel(index: Int): Boolean {
        if (labelInterval <= 1) return true
        return if (oneBasedLabelInterval) {
            (index + 1) % labelInterval == 0
        } else {
            index % labelInterval == 0
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val chartContentWidth = maxWidth - axisWidth - chartGap
        val barSlotWidthDp = chartContentWidth / buckets.size.coerceAtLeast(1)

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(axisWidth)
                    .height(chartHeightDp),
            ) {
                axisLabels.forEachIndexed { index, label ->
                    val fraction = axisFractions.getOrElse(index) { 0f }
                    val yOffset = chartHeightDp * (1f - fraction)
                    Text(
                        text = label,
                        style = labelStyle,
                        color = if (showZeroLabel && index == axisLabels.lastIndex) zeroLabelColor else axisLabelColor,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = yOffset - 8.dp)
                            .width(axisWidth),
                    )
                }
            }

            Spacer(modifier = Modifier.width(chartGap))

            Column(modifier = Modifier.weight(1f)) {
                Canvas(
                    modifier = Modifier
                        .width(chartContentWidth)
                        .height(chartHeightDp)
                        .clipToBounds(),
                ) {
                    val chartHeight = size.height
                    val barSlotWidth = size.width / buckets.size.coerceAtLeast(1)
                    axisFractions.forEach { fraction ->
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
                        .padding(top = 4.dp, end = if (rotateLabels) 8.dp else 0.dp),
                ) {
                    buckets.forEachIndexed { index, bucket ->
                        val showLabel = shouldShowLabel(index)
                        Column(
                            modifier = Modifier
                                .width(barSlotWidthDp)
                                .padding(horizontal = if (index == 0 || index == buckets.lastIndex) 0.dp else 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
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
}
