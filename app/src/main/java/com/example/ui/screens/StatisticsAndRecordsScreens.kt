package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferences
import com.example.ui.components.MonthlyActivityBarChart
import com.example.ui.components.PremiumCrownIcon
import com.example.ui.components.PremiumFeatureBadge
import com.example.ui.components.StatHighlightCard
import com.example.util.UnitFormatter
import com.example.viewmodel.FishingAnalytics
import com.example.viewmodel.PersonalMilestone
import com.example.viewmodel.PremiumAnglerBadge
import com.example.viewmodel.SpeciesComparisonMetric
import com.example.viewmodel.YearlyCatchPoint

@Composable
fun StatisticsAndRecordsScreen(
    analytics: FishingAnalytics,
    preferences: UserPreferences,
    isPremium: Boolean,
    initialTabIndex: Int = 0,
    onExportPdf: (Uri) -> Unit = {},
    onExportCsv: (Uri) -> Unit = {},
    onOpenPremium: () -> Unit
) {
    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Analytics & Records",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Track species distribution, seasonal trends, and personal bests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PremiumFeatureBadge(
                isLocked = !isPremium,
                label = if (isPremium) "PRO ANALYTICS" else "UPGRADE PRO",
                onClick = onOpenPremium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Statistics") },
                modifier = Modifier.testTag("tab_statistics")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Personal Records") },
                modifier = Modifier.testTag("tab_personal_records")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            StatisticsTabContent(
                analytics = analytics,
                preferences = preferences,
                isPremium = isPremium,
                onExportPdf = onExportPdf,
                onExportCsv = onExportCsv,
                onOpenPremium = onOpenPremium
            )
        } else {
            PersonalRecordsTabContent(
                analytics = analytics,
                preferences = preferences,
                isPremium = isPremium,
                onOpenPremium = onOpenPremium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandalonePersonalRecordsScreen(
    analytics: FishingAnalytics,
    preferences: UserPreferences,
    isPremium: Boolean = false,
    onOpenPremium: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Records & Milestones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.widthIn(max = 760.dp)) {
                PersonalRecordsTabContent(
                    analytics = analytics,
                    preferences = preferences,
                    isPremium = isPremium,
                    onOpenPremium = onOpenPremium
                )
            }
        }
    }
}

@Composable
private fun StatisticsTabContent(
    analytics: FishingAnalytics,
    preferences: UserPreferences,
    isPremium: Boolean,
    onExportPdf: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onOpenPremium: () -> Unit
) {
    var showYearlyView by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) onExportPdf(uri)
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) onExportCsv(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Basic Free-Plan Statistics Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatHighlightCard(
                    label = "Total Catches",
                    value = analytics.totalCatches.toString(),
                    icon = Icons.Filled.Phishing,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatHighlightCard(
                    label = "Total Sessions",
                    value = analytics.totalSessions.toString(),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatHighlightCard(
                    label = "Biggest Fish",
                    value = analytics.biggestByWeight?.let {
                        UnitFormatter.formatWeight(it.weightKg, preferences.weightUnit)
                    } ?: "—",
                    subtitle = analytics.biggestByWeight?.species,
                    icon = Icons.Filled.EmojiEvents,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatHighlightCard(
                    label = "Average Weight",
                    value = if (analytics.totalCatches > 0) {
                        UnitFormatter.formatWeight(analytics.averageWeightKg, preferences.weightUnit)
                    } else "—",
                    icon = Icons.Filled.Scale,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatHighlightCard(
                    label = "Top Species",
                    value = analytics.mostFrequentSpecies ?: "—",
                    icon = Icons.Filled.Favorite,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatHighlightCard(
                    label = "Best Fishing Month",
                    value = analytics.bestFishingMonth ?: "—",
                    icon = Icons.Filled.CalendarMonth,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Basic 6-Month Catch History Chart (Free)
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Catch History Chart",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Recent 6-month catch progression",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    MonthlyActivityBarChart(points = analytics.monthlyHistory)
                }
            }
        }

        // Basic Species Distribution Breakdown (Free)
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Most Caught Species Breakdown", style = MaterialTheme.typography.titleMedium)
                    if (analytics.speciesBreakdown.isEmpty()) {
                        Text(
                            text = "Log catches to view species distribution bars.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        analytics.speciesBreakdown.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.species, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "${item.count} (${(item.percentage * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { item.percentage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // PREMIUM SECTION 1: Monthly (12-Mo) & Yearly Performance Charts
        item {
            PremiumSectionCard(
                title = "Monthly & Yearly Performance",
                icon = Icons.Filled.BarChart,
                isPremium = isPremium,
                lockedDescription = "Unlock 12-month seasonal curves and multi-year catch & trophy performance charts with FishLog Premium.",
                onOpenPremium = onOpenPremium,
                testTag = "premium_yearly_charts_card"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showYearlyView,
                        onClick = { showYearlyView = false },
                        label = { Text("12-Month Trend") }
                    )
                    FilterChip(
                        selected = showYearlyView,
                        onClick = { showYearlyView = true },
                        label = { Text("Multi-Year Comparison") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (!showYearlyView) {
                    MonthlyActivityBarChart(points = analytics.full12MonthHistory)
                } else {
                    YearlyComparisonBarChart(
                        points = analytics.yearlyHistory,
                        preferences = preferences
                    )
                }
            }
        }

        // PREMIUM SECTION 2: Species Comparison Matrix
        item {
            PremiumSectionCard(
                title = "Species Comparison",
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                isPremium = isPremium,
                lockedDescription = "Compare your target species side-by-side by trophy weight, average size, maximum length, and top-producing lure.",
                onOpenPremium = onOpenPremium,
                testTag = "premium_species_comparison_card"
            ) {
                if (analytics.speciesComparisons.isEmpty()) {
                    Text(
                        text = "Log catches across species to compare head-to-head metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    analytics.speciesComparisons.take(6).forEachIndexed { idx, metric ->
                        if (idx > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        SpeciesComparisonRow(metric = metric, preferences = preferences)
                    }
                }
            }
        }

        // PREMIUM SECTION 3: Advanced Catch Analytics (Technique Ring + Water Clarity + Release Rate)
        item {
            PremiumSectionCard(
                title = "Advanced Catch Analytics",
                icon = Icons.Filled.WaterDrop,
                isPremium = isPremium,
                lockedDescription = "Analyze fishing method conversion rings, water condition patterns, and catch-and-release conservation ratios.",
                onOpenPremium = onOpenPremium,
                testTag = "premium_advanced_analytics_card"
            ) {
                Text(
                    text = "Fishing Method Efficiency",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                MethodDonutChart(methodBreakdown = analytics.methodBreakdown)

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Conservation C&R Rate",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${analytics.releaseRatePercent}% Released",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (analytics.waterConditionBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Top Water Conditions",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    analytics.waterConditionBreakdown.entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .forEach { (cond, cnt) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cond, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "$cnt catches",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                }
            }
        }

        // PREMIUM SECTION 4: Export Fishing Records to PDF & CSV
        item {
            PremiumSectionCard(
                title = "Export Fishing Records",
                icon = Icons.Filled.FileDownload,
                isPremium = isPremium,
                lockedDescription = "Export your entire angler logbook and personal records to printable PDF reports and CSV spreadsheets.",
                onOpenPremium = onOpenPremium,
                testTag = "premium_stats_export_card"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("FishLog_Angler_Report.pdf") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                    OutlinedButton(
                        onClick = { csvLauncher.launch("FishLog_Catches.csv") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV")
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPremium: Boolean,
    lockedDescription: String,
    onOpenPremium: () -> Unit,
    testTag: String,
    unlockedContent: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isPremium) Modifier.clickable { onOpenPremium() } else Modifier)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                PremiumFeatureBadge(
                    isLocked = !isPremium,
                    onClick = if (!isPremium) onOpenPremium else null
                )
            }

            if (isPremium) {
                unlockedContent()
            } else {
                Text(
                    text = lockedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenPremium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF134631),
                        contentColor = Color(0xFF2EE585)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked Premium Feature",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFCA45)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unlock with FishLog Premium", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun YearlyComparisonBarChart(
    points: List<YearlyCatchPoint>,
    preferences: UserPreferences
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = Color(0xFF2EE585)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxVal = (points.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val slotW = size.width / points.size
            val barW = (slotW * 0.44f).coerceAtMost(46.dp.toPx())
            points.forEachIndexed { idx, pt ->
                val cx = slotW * idx + slotW / 2f
                val left = cx - barW / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 8.dp.toPx()),
                    size = Size(barW, size.height - 16.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                if (pt.count > 0) {
                    val ratio = (pt.count.toFloat() / maxVal.toFloat()).coerceIn(0.14f, 1f)
                    val availH = size.height - 16.dp.toPx()
                    val barH = availH * ratio
                    val topY = 8.dp.toPx() + (availH - barH)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(accentColor, primaryColor),
                            startY = topY,
                            endY = topY + barH
                        ),
                        topLeft = Offset(left, topY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${pt.count} fish",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pt.yearLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pt.biggestWeightKg > 0.0) {
                        Text(
                            text = "Max ${UnitFormatter.formatWeight(pt.biggestWeightKg, preferences.weightUnit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeciesComparisonRow(
    metric: SpeciesComparisonMetric,
    preferences: UserPreferences
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = metric.species,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${metric.count} catches",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Max: ${UnitFormatter.formatWeight(metric.maxWeightKg, preferences.weightUnit)} (${UnitFormatter.formatLength(metric.maxLengthCm, preferences.lengthUnit)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Avg: ${UnitFormatter.formatWeight(metric.averageWeightKg, preferences.weightUnit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Best Bait / Lure: ${metric.topBait}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MethodDonutChart(methodBreakdown: Map<String, Int>) {
    if (methodBreakdown.isEmpty()) {
        Text(
            text = "Log catches with methods to populate technique breakdown.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val palette = listOf(
        Color(0xFF2EE585),
        Color(0xFFFFCA45),
        Color(0xFF57A8D8),
        Color(0xFF9B87F5),
        Color(0xFFF26D6D)
    )
    val total = methodBreakdown.values.sum().coerceAtLeast(1)
    val entries = methodBreakdown.entries.sortedByDescending { it.value }.take(5)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Canvas(modifier = Modifier.size(106.dp)) {
            var startAngle = -90f
            entries.forEachIndexed { idx, entry ->
                val sweep = (entry.value.toFloat() / total.toFloat()) * 360f
                drawArc(
                    color = palette[idx % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.forEachIndexed { idx, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette[idx % palette.size])
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${entry.key}: ${entry.value}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalRecordsTabContent(
    analytics: FishingAnalytics,
    preferences: UserPreferences,
    isPremium: Boolean,
    onOpenPremium: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatHighlightCard(
                    label = "Biggest Fish Caught",
                    value = analytics.biggestByWeight?.let {
                        UnitFormatter.formatWeight(it.weightKg, preferences.weightUnit)
                    } ?: "—",
                    subtitle = analytics.biggestByWeight?.species ?: "Not set yet",
                    icon = Icons.Filled.EmojiEvents,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatHighlightCard(
                    label = "Longest Fish Caught",
                    value = analytics.longestFish?.let {
                        UnitFormatter.formatLength(it.lengthCm, preferences.lengthUnit)
                    } ?: "—",
                    subtitle = analytics.longestFish?.species ?: "Not set yet",
                    icon = Icons.Filled.Straighten,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatHighlightCard(
                    label = "Most Catches / Session",
                    value = if (analytics.mostCatchesInOneSession > 0) {
                        "${analytics.mostCatchesInOneSession} fish"
                    } else "—",
                    subtitle = "Single trip record",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                StatHighlightCard(
                    label = "Favorite Location",
                    value = analytics.favoriteLocation ?: "—",
                    subtitle = "Most productive water",
                    icon = Icons.Filled.Place,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            StatHighlightCard(
                label = "Favorite Bait / Lure",
                value = analytics.favoriteBait ?: "—",
                subtitle = "Top strike-producing tackle",
                icon = Icons.Filled.Phishing,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Exclusive Premium Angler Badges Section
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exclusive Premium Badges",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                PremiumFeatureBadge(
                    isLocked = !isPremium,
                    onClick = if (!isPremium) onOpenPremium else null
                )
            }
        }

        items(analytics.premiumBadges, key = { it.id }) { proBadge ->
            PremiumBadgeRowCard(
                badge = proBadge,
                isPremium = isPremium,
                onOpenPremium = onOpenPremium
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Personal Fishing Milestones",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(analytics.milestones, key = { it.id }) { milestone ->
            MilestoneCard(milestone = milestone)
        }
    }
}

@Composable
private fun PremiumBadgeRowCard(
    badge: PremiumAnglerBadge,
    isPremium: Boolean,
    onOpenPremium: () -> Unit
) {
    val earned = isPremium && badge.unlocked
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (earned) Color(0xFF0E2638) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isPremium) Modifier.clickable { onOpenPremium() } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (earned) Color(0xFF154D36) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isPremium) {
                        PremiumCrownIcon(
                            modifier = Modifier.size(22.dp),
                            tint = if (earned) Color(0xFFFFCA45) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked Premium Badge",
                            tint = Color(0xFFFFCA45),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (earned) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (earned) Color(0xFF9EB9AD) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PremiumFeatureBadge(
                isLocked = !isPremium,
                label = if (earned) "EARNED" else "PRO",
                onClick = if (!isPremium) onOpenPremium else null
            )
        }
    }
}

@Composable
private fun MilestoneCard(milestone: PersonalMilestone) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.achieved) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (milestone.achieved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (milestone.achieved) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (milestone.achieved) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = milestone.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (milestone.achieved) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Unlocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
