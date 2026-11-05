package com.countmymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Lang
import com.countmymacros.data.Routine
import com.countmymacros.data.Suggestions
import com.countmymacros.data.Workout
import com.countmymacros.data.fmt
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Train tab: this week, the next routine, your routines and the recent history. */
@Composable
fun TrainScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val live = vm.session?.takeIf { it.live }
    val freeName = stringResource(R.string.free_workout)
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val today = vm.today
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val trainedDates = vm.recentWorkouts.map { LocalDate.parse(it.date) }.toSet()
    val weekCount = trainedDates.count { it >= monday && it <= monday.plusDays(6) }
    val next = Suggestions.nextRoutine(vm.routines, vm.recentWorkouts)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = vm.trainList,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(stringResource(R.string.tab_train), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        pluralStringResource(R.plurals.workouts_week, weekCount, weekCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { DayStrip(today, onSelect = null, marked = trainedDates) }

            item {
                when {
                    live != null -> LiveHero(live, onResume = { vm.push(Screen.RunWorkout) }, onDiscard = { confirmDiscard = true })
                    next != null -> NextRoutineHero(vm, next)
                    else -> HeroCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.no_routines), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = vm::newRoutine) { Text(stringResource(R.string.new_routine)) }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.routines),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = vm::newRoutine) {
                        Icon(Icons.Filled.Add, null)
                        Text("  " + stringResource(R.string.new_routine))
                    }
                }
            }
            items(vm.routines, key = { "r" + it.id }) { r ->
                RoutineCard(vm, r, canStart = live == null, onDelete = { routineToDelete = r })
            }

            item {
                Text(
                    stringResource(R.string.history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (vm.recentWorkouts.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_workouts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(vm.recentWorkouts, key = { "w" + it.id }) { w ->
                WorkoutRow(w, onDelete = { workoutToDelete = w })
            }
        }

        if (live == null) {
            ExtendedFloatingActionButton(
                onClick = { vm.startWorkout(null, freeName) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.free_workout)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    routineToDelete?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.delete_routine_title),
            text = r.name,
            confirm = stringResource(R.string.delete),
            onConfirm = { vm.deleteRoutine(r); routineToDelete = null },
            onDismiss = { routineToDelete = null },
        )
    }
    workoutToDelete?.let { w ->
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_title),
            text = w.name,
            confirm = stringResource(R.string.delete),
            onConfirm = { vm.deleteWorkout(w); workoutToDelete = null },
            onDismiss = { workoutToDelete = null },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard),
            text = live?.name.orEmpty(),
            confirm = stringResource(R.string.discard),
            onConfirm = { vm.discardSession(); confirmDiscard = false },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** Muscles worked by a routine's exercises, most frequent first (at most three). */
@Composable
private fun musclesOf(vm: AppViewModel, r: Routine): List<String> {
    val muscles = r.exercises.mapNotNull { vm.exercise(it.exerciseId)?.muscle }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
    return muscles.map { stringResource(it.label) }
}

@Composable
private fun LiveHero(live: Session, onResume: () -> Unit, onDiscard: () -> Unit) {
    val now by rememberNowMs()
    HeroCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workout_in_progress).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(live.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(clock((now - live.startMs) / 1000), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onResume, Modifier.weight(1f)) { Text(stringResource(R.string.continue_workout)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) }
            }
        }
    }
}

@Composable
private fun NextRoutineHero(vm: AppViewModel, r: Routine) {
    val sets = r.exercises.sumOf { it.sets.size }
    HeroCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.next_routine).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(r.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                musclesOf(vm, r).take(2).forEach { TagChip(it) }
                TagChip(stringResource(R.string.exercises_sets, r.exercises.size, sets))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startWorkout(r, r.name) }, Modifier.weight(1f)) { Text(stringResource(R.string.start)) }
                FilledTonalButton(onClick = { vm.logWorkout(r) }) { Text(stringResource(R.string.log_done)) }
            }
        }
    }
}

@Composable
private fun RoutineCard(vm: AppViewModel, r: Routine, canStart: Boolean, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val summary = r.exercises.joinToString(", ") { vm.exercise(it.exerciseId)?.name(Lang.current) ?: it.exerciseId }
    val sets = r.exercises.sumOf { it.sets.size }
    SurfaceCard {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; vm.editRoutine(r) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                musclesOf(vm, r).take(2).forEach { TagChip(it, tone = TagTone.PRIMARY) }
                if (sets > 0) TagChip(pluralStringResource(R.plurals.sets_n, sets, sets))
            }
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    Modifier.padding(top = 6.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(top = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startWorkout(r, r.name) }, enabled = canStart) { Text(stringResource(R.string.start)) }
                OutlinedButton(onClick = { vm.logWorkout(r) }, enabled = canStart) { Text(stringResource(R.string.log_done)) }
            }
        }
    }
}

@Composable
private fun WorkoutRow(w: Workout, onDelete: () -> Unit) {
    val d = LocalDate.parse(w.date)
    SurfaceCard {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(d.dayOfMonth.toString(), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase().take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(w.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(stringResource(R.string.workout_min, w.durationMin), icon = Icons.Filled.Timer)
                    TagChip(fmt(w.volumeKg) + " kg")
                    TagChip(stringResource(R.string.kcal_value, w.kcal.roundToInt()), tone = TagTone.WARM)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = if (text.isNotBlank()) {
            { Text(text) }
        } else null,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
