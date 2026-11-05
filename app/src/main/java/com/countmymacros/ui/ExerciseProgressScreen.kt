package com.countmymacros.ui

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.ExercisePoint
import com.countmymacros.data.ExerciseStats
import com.countmymacros.data.Lang
import com.countmymacros.data.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the chart plots. [unit] is appended to the numbers ("" for reps). */
private enum class Metric(@StringRes val label: Int, val unit: String, val of: (ExercisePoint) -> Double) {
    WEIGHT(R.string.metric_weight, " kg", { it.bestKg }),
    E1RM(R.string.metric_1rm, " kg", { it.e1rm }),
    VOLUME(R.string.metric_volume, " kg", { it.volume }),
    REPS(R.string.metric_reps, "", { it.bestReps.toDouble() }),
}

/** How one exercise has progressed over the workouts it appeared in: a trend, the best so far and the sessions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(vm: AppViewModel, exerciseId: String) {
    val exercise = vm.exercise(exerciseId)

    @Suppress("ProduceStateDoesNotAssignValue")
    val points by produceState<List<ExercisePoint>?>(null, exerciseId, vm.recentWorkouts) {
        value = withContext(Dispatchers.IO) { ExerciseStats.series(vm.repo.allWorkouts(), exerciseId) }
    }
    val series = points
    // Exercises done without added weight (bodyweight, cardio) chart their reps instead of kilos.
    val metrics = if (series != null && series.none { it.bestKg > 0 }) listOf(Metric.REPS, Metric.VOLUME)
    else listOf(Metric.WEIGHT, Metric.E1RM, Metric.VOLUME)
    var chosen by rememberSaveable { mutableIntStateOf(0) }
    val metric = metrics[chosen.coerceIn(0, metrics.lastIndex)]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name(Lang.current) ?: exerciseId, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (series.isNullOrEmpty()) {
                if (series != null) item {
                    Text(stringResource(R.string.no_strength), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@LazyColumn
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    metrics.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = m == metric,
                            onClick = { chosen = i },
                            shape = SegmentedButtonDefaults.itemShape(i, metrics.size),
                        ) { Text(stringResource(m.label), maxLines = 1) }
                    }
                }
            }
            item { TrendCard(series, metric) }
            item {
                Text(
                    stringResource(R.string.sessions_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(series.asReversed()) { p -> SessionRow(p) }
        }
    }
}

@Composable
private fun TrendCard(series: List<ExercisePoint>, metric: Metric) {
    val values = series.map(metric.of)
    val latest = values.last()
    val best = values.max()
    val diff = if (values.size >= 2) latest - values.first() else null
    SurfaceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        fmt(latest) + metric.unit,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.best_so_far, fmt(best) + metric.unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (diff != null && abs(diff) > 1e-9) {
                    TagChip(
                        (if (diff > 0) "▲ " else "▼ ") + fmt(abs(diff)) + metric.unit,
                        tone = if (diff > 0) TagTone.PRIMARY else TagTone.WARM,
                    )
                }
            }
            // A single session is a dot, not a trend: Sparkline draws just that.
            Sparkline(values, Modifier.fillMaxWidth().height(140.dp))
        }
    }
}

@Composable
private fun SessionRow(p: ExercisePoint) {
    SurfaceCard {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fullDate(LocalDate.parse(p.date)), fontWeight = FontWeight.Medium)
                Text(
                    if (p.bestKg > 0) "${fmt(p.bestKg)} kg × ${p.bestReps}" else "${p.bestReps} reps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p.volume > 0) {
                Text(
                    stringResource(R.string.volume_value, p.volume.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
