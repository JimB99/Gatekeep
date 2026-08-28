package com.gatekeep.app.ui.stats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.data.AppUsageStat
import com.gatekeep.app.data.StatsRangeKind
import com.gatekeep.app.data.TopAppUsage
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.AppIcon
import com.gatekeep.app.ui.components.GatekeepFilterChip
import com.gatekeep.app.ui.viewmodel.StatsViewModel
import com.gatekeep.app.util.formatDurationMs
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val rangeKind by viewModel.rangeKind.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val topApps by viewModel.topApps.collectAsState()
    val trackedApps by viewModel.trackedApps.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val overrideCount by viewModel.overrideCount.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()
    val disabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    fun shiftPrevious() = viewModel.shiftPeriod(false)
    fun shiftNext() {
        if (canGoForward) viewModel.shiftPeriod(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
            .then(if (isLandscape) Modifier.verticalScroll(scrollState) else Modifier)

        Column(modifier = contentModifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatsRangeKind.entries.forEach { kind ->
                    GatekeepFilterChip(
                        selected = rangeKind == kind,
                        onClick = { viewModel.setRangeKind(kind) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                when (kind) {
                                    StatsRangeKind.day -> stringResource(R.string.range_day)
                                    StatsRangeKind.week -> stringResource(R.string.range_week)
                                    StatsRangeKind.month -> stringResource(R.string.range_month)
                                    StatsRangeKind.year -> stringResource(R.string.range_year)
                                },
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .periodSwipe(
                        onPrevious = ::shiftPrevious,
                        onNext = ::shiftNext,
                        canGoNext = canGoForward,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::shiftPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous))
                }
                Text(
                    text = overview.rangeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = { viewModel.resetToCurrentPeriod() },
                        ),
                )
                IconButton(
                    onClick = ::shiftNext,
                    enabled = canGoForward,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        stringResource(R.string.next),
                        tint = if (canGoForward) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            disabledIconColor
                        },
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .periodSwipe(
                        onPrevious = ::shiftPrevious,
                        onNext = ::shiftNext,
                        canGoNext = canGoForward,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.screen_time), style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatDurationMs(overview.totalUsageMs),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (overview.chartBuckets.isNotEmpty()) {
                        StatsBarChart(
                            buckets = overview.chartBuckets,
                            scaleMs = overview.scaleMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            enableHorizontalScroll = false,
                        )
                    }
                }
            }
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                listOf(R.string.tab_overview, R.string.tab_top_apps, R.string.tab_tracked).forEachIndexed { index, titleRes ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(stringResource(titleRes)) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                } else {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                },
            ) { page ->
                when (page) {
                    0 -> OverviewPage(
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onProfileSelect = viewModel::setProfileId,
                        streak = streak,
                        overrideCount = overrideCount,
                    )
                    1 -> TopAppsPage(topApps)
                    else -> TrackedAppsPage(trackedApps)
                }
            }
        }
    }
}

@Composable
private fun OverviewPage(
    profiles: List<com.gatekeep.domain.model.Profile>,
    selectedProfileId: Long?,
    onProfileSelect: (Long?) -> Unit,
    streak: com.gatekeep.domain.model.StreakInfo,
    overrideCount: Int,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    GatekeepFilterChip(
                        selected = selectedProfileId == profile.id,
                        onClick = { onProfileSelect(profile.id) },
                        label = { Text(profile.name) },
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.current_streak_format, streak.currentStreakDays))
                    Text(stringResource(R.string.longest_streak_format, streak.longestStreakDays))
                    Text(stringResource(R.string.overrides_format, overrideCount))
                }
            }
        }
    }
}

@Composable
private fun TopAppsPage(apps: List<TopAppUsage>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(apps) { stat ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(stat.packageName, modifier = Modifier.height(40.dp))
                    Column {
                        Text(stat.label)
                        Text(formatDurationMs(stat.usageMs))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackedAppsPage(apps: List<AppUsageStat>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(apps) { stat ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(stat.packageName, modifier = Modifier.height(40.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stat.label)
                        Text(
                            "${formatDurationMs(stat.usageMs)} / ${stat.limitMs?.let { formatDurationMs(it) } ?: stringResource(R.string.no_limit_dash)}",
                        )
                        if (stat.limitMs != null && stat.limitMs > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (stat.usageMs.toFloat() / stat.limitMs).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
