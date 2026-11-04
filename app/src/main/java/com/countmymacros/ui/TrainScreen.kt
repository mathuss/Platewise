package com.countmymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Lang
import com.countmymacros.data.Routine
import com.countmymacros.data.Workout
import com.countmymacros.data.fmt
import java.time.LocalDate
import kotlin.math.roundToInt

/** Train tab: the workout in progress, routines to start or log, and the recent history. */
@Composable
fun TrainScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val live = vm.session?.takeIf { it.live }
    val freeName = stringResource(R.string.free_workout)
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (live != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.workout_in_progress), fontWeight = FontWeight.SemiBold)
                        Text(live.name, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.push(Screen.RunWorkout) }) { Text(stringResource(R.string.resume)) }
                            TextButton(onClick = { confirmDiscard = true }) { Text(stringResource(R.string.discard)) }
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { vm.startWorkout(null, freeName) },
                enabled = live == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.free_workout)) }
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
        if (vm.routines.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_routines),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(vm.routines, key = { "r" + it.id }) { r ->
            RoutineCard(vm, r, canStart = live == null, onDelete = { routineToDelete = r })
        }

        item { SectionTitle(stringResource(R.string.history)) }
        if (vm.recentWorkouts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_workouts),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(vm.recentWorkouts, key = { "w" + it.id }) { w ->
            WorkoutRow(w, onDelete = { workoutToDelete = w })
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

@Composable
private fun RoutineCard(vm: AppViewModel, r: Routine, canStart: Boolean, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val summary = r.exercises.joinToString(", ") { vm.exercise(it.exerciseId)?.name(Lang.current) ?: it.exerciseId }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; vm.editRoutine(r) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(top = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startWorkout(r, r.name) }, enabled = canStart) { Text(stringResource(R.string.start)) }
                OutlinedButton(onClick = { vm.logWorkout(r) }, enabled = canStart) { Text(stringResource(R.string.log_done)) }
            }
        }
    }
}

@Composable
private fun WorkoutRow(w: Workout, onDelete: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dayLabel(LocalDate.parse(w.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.workout_stats, w.durationMin, w.setCount, fmt(w.volumeKg), w.kcal.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
        HorizontalDivider()
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
