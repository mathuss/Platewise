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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import com.countmymacros.ui.theme.MacroColors
import com.countmymacros.ui.theme.macroTextColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    @Suppress("ProduceStateDoesNotAssignValue") // K2 lint false positive: `value` is assigned
    val week by produceState(emptyList<Entry>(), vm.entries) {
        value = withContext(Dispatchers.IO) { vm.repo.range(days.first(), days.last()) }
    }
    LaunchedEffect(Unit) { vm.loadWeights() }

    val perDay = days.map { d -> week.filter { it.date == d.toString() }.fold(Macros.ZERO) { a, e -> a + e.macros } }
    val logged = perDay.filter { it.kcal > 0 }
    val avg = if (logged.isEmpty()) Macros.ZERO else logged.fold(Macros.ZERO) { a, m -> a + m }.scaled(1.0 / logged.size)
    val target = vm.targets.daily

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.calories_7days), style = MaterialTheme.typography.titleMedium)
                    WeekChart(days, perDay.map { it.kcal }, target.kcal)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.daily_average), style = MaterialTheme.typography.titleMedium)
                    if (logged.isEmpty()) {
                        Text(stringResource(R.string.no_logs_week), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            pluralStringResource(
                                R.plurals.avg_line, logged.size,
                                avg.kcal.roundToInt(), target.kcal.roundToInt(), logged.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val (proteinText, carbsText, fatText) = macroTextColors()
                        MacroBar(stringResource(R.string.protein), avg.protein, target.protein, MacroColors.Protein, proteinText)
                        MacroBar(stringResource(R.string.carbs), avg.carbs, target.carbs, MacroColors.Carbs, carbsText)
                        MacroBar(stringResource(R.string.fat), avg.fat, target.fat, MacroColors.Fat, fatText)
                    }
                }
            }
        }
        item { WeightCard(vm) }
        items(vm.weights.take(30), key = { it.date }) { w ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(LocalDate.parse(w.date)), Modifier.weight(1f))
                Text("${fmt(w.kg)} kg", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { vm.deleteWeight(w) }) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun WeekChart(days: List<LocalDate>, kcal: List<Double>, target: Double) {
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val maxV = maxOf(target * 1.25, kcal.maxOrNull() ?: 0.0, 1.0)
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)) {
        val slot = size.width / days.size
        val barW = slot * 0.55f
        kcal.forEachIndexed { i, v ->
            val h = (v / maxV).toFloat() * size.height
            drawRoundRect(
                color = if (target > 0 && v > target * 1.1) over else bar,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
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
        days.forEach { d ->
            Text(
                d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WeightCard(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.weight_title), style = MaterialTheme.typography.titleMedium)
            val latest = vm.weights.firstOrNull()
            val first = vm.weights.lastOrNull()
            if (latest != null && first != null && latest != first) {
                val diff = latest.kg - first.kg
                Text(
                    stringResource(
                        R.string.weight_since,
                        (if (diff > 0) "+" else "") + fmt(diff),
                        dayLabel(LocalDate.parse(first.date)).lowercase(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(text, { text = it }, stringResource(R.string.todays_weight), Modifier.weight(1f), "kg")
                Button(
                    onClick = { parseNum(text)?.let { vm.addWeight(it); text = "" } },
                    enabled = (parseNum(text) ?: 0.0) in 20.0..400.0,
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text(stringResource(R.string.log_weight)) }
            }
            Text(
                stringResource(R.string.weight_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
