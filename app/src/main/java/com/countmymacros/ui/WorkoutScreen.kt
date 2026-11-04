package com.countmymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Burn
import com.countmymacros.data.Sex
import com.countmymacros.data.Workout
import com.countmymacros.data.fmt
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * A workout being recorded. Live ones run a clock and a rest timer that starts when a set is
 * ticked; the ones logged after the fact ask for the date, start time and duration instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(vm: AppViewModel) {
    val s = vm.session
    if (s == null) {
        LaunchedEffect(Unit) { vm.pop() }
        return
    }
    val female = vm.profile.sex == Sex.FEMALE
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (s.live) {
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val endMs = if (s.live) now else s.startMs + s.durationMin * 60_000L
    val seconds = ((endMs - s.startMs) / 1000).coerceAtLeast(0)
    val kcal = Burn.kcal(seconds, vm.profile.weightKg)
    val setsDone = s.exercises.sumOf { e -> e.sets.count { it.done } }
    val restLeft = if (s.live) ((s.restEndMs - now) / 1000).coerceAtLeast(0) else 0L
    var confirmDiscard by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.name) },
                navigationIcon = {
                    IconButton(onClick = {
                        // A workout logged after the fact isn't saved anywhere until finished, so leaving drops it.
                        if (!s.live) vm.discardSession()
                        vm.pop()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = vm::finishSession, enabled = s.live || s.durationMin > 0) {
                        Text(stringResource(R.string.finish))
                    }
                },
            )
        },
        bottomBar = {
            if (restLeft > 0) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.rest_left, "${restLeft / 60}:${(restLeft % 60).toString().padStart(2, '0')}"),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = { vm.updateSession(s.copy(restEndMs = 0)) }) { Text(stringResource(R.string.skip)) }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = s.name,
                    onValueChange = { vm.updateSession(s.copy(name = it)) },
                    label = { Text(stringResource(R.string.workout_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!s.live) {
                item { LoggedTimeFields(s, vm::updateSession) }
            }
            item {
                Text(
                    stringResource(
                        R.string.workout_stats,
                        (seconds / 60).toInt(), setsDone, fmt(Workout.volumeKg(s.exercises)), kcal.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(s.exercises) { i, item ->
                ExerciseCard(
                    exercise = vm.exercise(item.exerciseId),
                    item = item,
                    previous = vm.previous[item.exerciseId].orEmpty(),
                    showDone = true,
                    female = female,
                    onChange = { changed ->
                        // Ticking a set starts that exercise's rest timer.
                        val ticked = changed.sets.count { it.done } > item.sets.count { it.done }
                        val restEnd = if (s.live && ticked) System.currentTimeMillis() + changed.restSeconds * 1000L else s.restEndMs
                        vm.updateSession(s.copy(exercises = s.exercises.replaced(i, changed), restEndMs = restEnd))
                    },
                    onRemove = { vm.updateSession(s.copy(exercises = s.exercises.filterIndexed { j, _ -> j != i })) },
                    onMoveUp = if (i > 0) ({ vm.updateSession(s.copy(exercises = s.exercises.moved(i, i - 1))) }) else null,
                    onMoveDown = if (i < s.exercises.lastIndex) ({ vm.updateSession(s.copy(exercises = s.exercises.moved(i, i + 1))) }) else null,
                )
            }
            item {
                OutlinedButton(onClick = { vm.pickExercises(PickerTarget.SESSION) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("  " + stringResource(R.string.add_exercises))
                }
            }
            if (s.live) {
                item {
                    TextButton(onClick = { confirmDiscard = true }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard),
            text = s.name,
            confirm = stringResource(R.string.discard),
            onConfirm = { confirmDiscard = false; vm.discardSession(); vm.pop() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** Date, start time and duration of a workout that already happened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoggedTimeFields(s: Session, onChange: (Session) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(s.startMs).atZone(zone)
    var picking by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_date), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { picking = true }) { Text(fullDate(s.date)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_start), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            NumCell(
                start.hour.toDouble(),
                { onChange(s.copy(startMs = start.withHour(it.roundToInt().coerceIn(0, 23)).toInstant().toEpochMilli())) },
                Modifier.width(72.dp),
            )
            Text("  :  ")
            NumCell(
                start.minute.toDouble(),
                { onChange(s.copy(startMs = start.withMinute(it.roundToInt().coerceIn(0, 59)).toInstant().toEpochMilli())) },
                Modifier.width(72.dp),
                pad = 2,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_duration), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            NumCell(
                s.durationMin.toDouble(),
                { onChange(s.copy(durationMin = it.roundToInt().coerceIn(0, 24 * 60))) },
                Modifier.width(96.dp),
            )
        }
    }

    if (picking) {
        // The picker works in UTC midnights; convert from / to the local date.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = s.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(s.copy(startMs = start.with(day).toInstant().toEpochMilli()))
                    }
                    picking = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}
