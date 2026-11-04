package com.countmymacros.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Entry
import com.countmymacros.data.Macros
import com.countmymacros.data.Stats
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import com.countmymacros.ui.theme.MacroColors
import com.countmymacros.ui.theme.macroTextColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var period by rememberSaveable { mutableIntStateOf(0) } // 0 = 7 days, 1 = 30 days, 2 = weight
    val today = LocalDate.now()
    val span = if (period == 1) 30 else 7
    val days = (span - 1 downTo 0).map { today.minusDays(it.toLong()) }
    @Suppress("ProduceStateDoesNotAssignValue") // K2 lint false positive: `value` is assigned
    val entries by produceState(emptyList<Entry>(), days.first(), vm.entries) {
        value = withContext(Dispatchers.IO) { vm.repo.range(days.first(), days.last()) }
    }
    @Suppress("ProduceStateDoesNotAssignValue")
    val trained by produceState(emptySet<LocalDate>(), days.first(), vm.recentWorkouts) {
        value = withContext(Dispatchers.IO) {
            vm.repo.workoutsBetween(days.first(), days.last()).map { LocalDate.parse(it.date) }.toSet()
        }
    }
    LaunchedEffect(Unit) { vm.loadWeights() }

    val kcalByDay = Stats.kcalPerDay(entries)
    val kcal = days.map { kcalByDay[it] ?: 0.0 }
    val target = vm.targets.daily
    val onTarget = Stats.daysOnTarget(kcalByDay, target.kcal)
    val streak = Stats.streak(kcalByDay.filterValues { it > 0 }.keys, today)
    val loggedDays = kcalByDay.values.count { it > 0 }
    val avgMacros = if (loggedDays == 0) Macros.ZERO
    else entries.fold(Macros.ZERO) { a, e -> a + e.macros }.scaled(1.0 / loggedDays)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.tab_progress), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            val labels = listOf(R.string.period_7, R.string.period_30, R.string.weight_title)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = period == i,
                        onClick = { period = i },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    ) { Text(stringResource(l), maxLines = 1) }
                }
            }
        }

        if (period != 2) {
            item {
                SurfaceCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.calories_per_day), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.target_label, target.kcal.roundToInt()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        KcalChart(days, kcal, target.kcal, trained, today, empty = loggedDays == 0)
                        if (loggedDays == 0) {
                            Text(
                                stringResource(R.string.progress_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        if (loggedDays == 0) "–" else fmtThousands(Stats.averageKcal(kcalByDay)),
                        stringResource(R.string.stat_avg), Modifier.weight(1f),
                    )
                    StatTile(
                        if (onTarget.second == 0) "–" else stringResource(R.string.on_target_value, onTarget.first, onTarget.second),
                        stringResource(R.string.stat_on_target), Modifier.weight(1f),
                    )
                    StatTile(streak.toString(), stringResource(R.string.stat_streak), Modifier.weight(1f))
                }
            }
            if (loggedDays > 0) {
                item {
                    SurfaceCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.daily_average), style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(
                                    R.plurals.avg_line, loggedDays,
                                    Stats.averageKcal(kcalByDay).roundToInt(), target.kcal.roundToInt(), loggedDays,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val (proteinText, carbsText, fatText) = macroTextColors()
                            MacroBar(stringResource(R.string.protein), avgMacros.protein, target.protein, MacroColors.Protein, proteinText)
                            MacroBar(stringResource(R.string.carbs), avgMacros.carbs, target.carbs, MacroColors.Carbs, carbsText)
                            MacroBar(stringResource(R.string.fat), avgMacros.fat, target.fat, MacroColors.Fat, fatText)
                        }
                    }
                }
            }
        }

        item { WeightCard(vm, large = period == 2) }
        if (period == 2) {
            items(vm.weights.take(60), key = { it.date }) { w ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(LocalDate.parse(w.date)), Modifier.weight(1f))
                    Text("${fmt(w.kg)} kg", fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { vm.deleteWeight(w) }) {
                        Icon(Icons.Filled.DeleteOutline, stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

/** 2133 → "2 133" with a thin grouping so it reads as a number, not a code. */
private fun fmtThousands(v: Double): String {
    val n = v.roundToInt()
    return if (n >= 1000) "${n / 1000} ${(n % 1000).toString().padStart(3, '0')}" else n.toString()
}

/** Bars per day with the target line. Today is solid, days over the target by more than 10 % turn amber. */
@Composable
private fun KcalChart(
    days: List<LocalDate>,
    kcal: List<Double>,
    target: Double,
    trained: Set<LocalDate>,
    today: LocalDate,
    empty: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val normal = cs.primary.copy(alpha = if (empty) 0.15f else 0.45f)
    val solid = cs.primary
    val over = cs.tertiary
    val line = cs.onSurfaceVariant
    val maxV = maxOf(target * 1.3, kcal.maxOrNull() ?: 0.0, 1.0)
    val thin = days.size > 10
    // An empty week still draws a skyline, so the chart reads as "waiting for data".
    val ghost = listOf(0.55, 0.7, 0.45, 0.8, 0.6, 0.5, 0.65)
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 14.dp)) {
        val slot = size.width / days.size
        val barW = slot * if (thin) 0.62f else 0.58f
        kcal.forEachIndexed { i, v ->
            val shown = if (empty) ghost[i % ghost.size] * target else v
            val h = (shown / maxV).toFloat() * size.height
            val color = when {
                empty -> normal
                target > 0 && v > target * 1.1 -> over
                days[i] == today -> solid
                v <= 0 -> cs.surfaceVariant
                else -> normal
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - maxOf(h, 3.dp.toPx())),
                size = Size(barW, maxOf(h, 3.dp.toPx())),
                cornerRadius = CornerRadius(if (thin) 3.dp.toPx() else 7.dp.toPx()),
            )
            if (days[i] in trained) {
                drawCircle(cs.tertiary, 2.5.dp.toPx(), Offset(i * slot + slot / 2, size.height - maxOf(h, 3.dp.toPx()) - 6.dp.toPx()))
            }
        }
        if (target > 0) {
            val y = size.height - (target / maxV).toFloat() * size.height
            drawLine(
                line, Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            val label = when {
                d == today -> stringResource(R.string.today)
                !thin -> d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase()
                i % 5 == 0 -> d.dayOfMonth.toString()
                else -> ""
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                color = if (d == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun WeightCard(vm: AppViewModel, large: Boolean) {
    var dialog by remember { mutableStateOf(false) }
    val latest = vm.weights.firstOrNull()
    val first = vm.weights.lastOrNull()
    val diff = if (latest != null && first != null && latest != first) latest.kg - first.kg else null
    SurfaceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.weight_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (diff != null) {
                    TagChip(
                        (if (diff > 0) "▲ " else "▼ ") + fmt(abs(diff)) + " kg",
                        tone = if (diff > 0) TagTone.WARM else TagTone.PRIMARY,
                    )
                }
            }
            if (latest == null) {
                Text(
                    stringResource(R.string.weight_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmt(latest.kg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(" kg", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Sparkline(
                        vm.weights.take(60).map { it.kg }.reversed(),
                        Modifier.weight(1f).height(if (large) 120.dp else 48.dp),
                    )
                }
                if (diff != null && first != null) {
                    Text(
                        stringResource(
                            R.string.weight_since,
                            (if (diff > 0) "+" else "") + fmt(diff),
                            dayLabel(LocalDate.parse(first.date)).lowercase(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = { dialog = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_weight_today)) }
            Text(
                stringResource(R.string.weight_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (dialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.todays_weight)) },
            text = { NumberField(text, { text = it }, stringResource(R.string.todays_weight), Modifier.fillMaxWidth(), "kg") },
            confirmButton = {
                TextButton(
                    onClick = { parseNum(text)?.let { vm.addWeight(it) }; dialog = false },
                    enabled = (parseNum(text) ?: 0.0) in 20.0..400.0,
                ) { Text(stringResource(R.string.log_weight)) }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
