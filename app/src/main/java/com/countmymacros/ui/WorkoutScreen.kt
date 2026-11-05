package com.countmymacros.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.countmymacros.R
import com.countmymacros.data.Burn
import com.countmymacros.data.Lang
import com.countmymacros.data.Sex
import com.countmymacros.data.Workout
import com.countmymacros.data.fmt
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** mm:ss, or h:mm:ss past the hour. */
internal fun clock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val two = { n: Long -> n.toString().padStart(2, '0') }
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
}

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
    val view = LocalView.current
    val female = vm.profile.sex == Sex.FEMALE
    // Not read here: only the small composables that show the time read it, so the clock doesn't recompose the exercise cards.
    val now = rememberNowMs(active = s.live)

    // The screen stays on while you train, so you don't unlock the phone between sets,
    // but not forever: a phone left open on the bench would burn its battery on a lit screen.
    var awake by remember { mutableStateOf(true) }
    val lastTouch = remember { longArrayOf(System.currentTimeMillis()) }
    fun touch() {
        lastTouch[0] = System.currentTimeMillis()
        awake = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { touch() }
    LaunchedEffect(s.live, awake) {
        if (!s.live || !awake) return@LaunchedEffect
        while (true) {
            val left = KEEP_AWAKE_MS - (System.currentTimeMillis() - lastTouch[0])
            if (left <= 0) {
                awake = false
                return@LaunchedEffect
            }
            delay(left)
        }
    }
    DisposableEffect(s.live && awake) {
        if (s.live && awake) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val setsDone = s.exercises.sumOf { e -> e.sets.count { it.done } }
    val setsTotal = s.exercises.sumOf { it.sets.size }
    var restTotal by remember { mutableLongStateOf(90L) }
    var restFor by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            // Watches every touch without consuming it.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    touch()
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.clickable { renaming = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Icon(
                            Icons.Filled.Edit, stringResource(R.string.rename_workout),
                            Modifier.padding(start = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // A workout logged after the fact isn't saved anywhere until finished, so leaving drops it.
                        if (!s.live) vm.discardSession()
                        vm.pop()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    Button(
                        onClick = vm::finishSession,
                        enabled = s.live || s.durationMin > 0,
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text(stringResource(R.string.finish)) }
                },
            )
        },
        bottomBar = {
            RestBar(
                s, now, restTotal, restFor,
                onExtend = {
                    restTotal += 15
                    vm.updateSession(s.copy(restEndMs = s.restEndMs + 15_000))
                },
                onSkip = { vm.updateSession(s.copy(restEndMs = 0)) },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!s.live) {
                item { LoggedTimeFields(s, vm::updateSession) }
            }
            item { WorkoutStats(s, now, vm.profile.weightKg, setsDone, setsTotal) }
            itemsIndexed(s.exercises) { i, item ->
                val exercise = vm.exercise(item.exerciseId)
                ExerciseCard(
                    exercise = exercise,
                    item = item,
                    previous = vm.previous[item.exerciseId].orEmpty(),
                    showDone = true,
                    female = female,
                    onChange = { changed ->
                        // Ticking a set starts that exercise's rest timer.
                        val ticked = changed.sets.count { it.done } > item.sets.count { it.done }
                        if (s.live && ticked) {
                            restTotal = changed.restSeconds.toLong().coerceAtLeast(1)
                            restFor = (exercise?.name(Lang.current) ?: "") + " · " + changed.sets.count { it.done } + "/" + changed.sets.size
                        }
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
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.workout_name)) },
            text = {
                OutlinedTextField(
                    value = s.name,
                    onValueChange = { vm.updateSession(s.copy(name = it)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.done_action)) } },
        )
    }
}

/** Without a touch for this long the screen is allowed to time out again. */
private const val KEEP_AWAKE_MS = 10 * 60_000L

/**
 * The wall clock, refreshed once a second only while the screen is visible (STARTED), so a workout left
 * in the background doesn't keep a timer running. Read it in the smallest composable that shows the time.
 */
@Composable
internal fun rememberNowMs(active: Boolean = true): LongState {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (active) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now.longValue = System.currentTimeMillis()
                    delay(1000)
                }
            }
        }
    }
    return now
}

/** Elapsed time, progress and the three stat tiles: the only part of the page that changes every second. */
@Composable
private fun WorkoutStats(s: Session, now: LongState, weightKg: Double, setsDone: Int, setsTotal: Int) {
    val endMs = if (s.live) now.longValue else s.startMs + s.durationMin * 60_000L
    val seconds = ((endMs - s.startMs) / 1000).coerceAtLeast(0)
    val kcal = Burn.kcal(seconds, weightKg)
    val volume = remember(s.exercises) { Workout.volumeKg(s.exercises) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.time_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (s.live) clock(seconds) else "${seconds / 60} min",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                stringResource(R.string.set_progress, setsDone, setsTotal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { if (setsTotal == 0) 0f else setsDone.toFloat() / setsTotal },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(fmt(volume), stringResource(R.string.volume_kg), Modifier.weight(1f))
            StatTile("$setsDone", stringResource(R.string.series_label), Modifier.weight(1f))
            StatTile("${kcal.roundToInt()}", stringResource(R.string.kcal_label), Modifier.weight(1f))
        }
    }
}

/** The rest countdown pinned to the bottom. Always composed (empty when not resting) so it can buzz when the rest ends. */
@Composable
private fun RestBar(s: Session, now: LongState, restTotal: Long, restFor: String, onExtend: () -> Unit, onSkip: () -> Unit) {
    val view = LocalView.current
    val restLeft = if (s.live) ((s.restEndMs - now.longValue + 999) / 1000).coerceAtLeast(0) else 0L
    var wasResting by remember { mutableStateOf(false) }

    // When the rest runs out: a short buzz (not when it was skipped).
    LaunchedEffect(restLeft > 0) {
        if (restLeft > 0) wasResting = true
        else if (wasResting) {
            wasResting = false
            // Vibration only: a sound would cut into the music people train with.
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    if (restLeft <= 0) return

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (restLeft.toFloat() / restTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(clock(restLeft), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.rest_title), fontWeight = FontWeight.SemiBold)
                if (restFor.isNotBlank()) {
                    Text(
                        restFor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            FilledTonalButton(onClick = onExtend, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(R.string.rest_plus))
            }
            Button(
                onClick = {
                    wasResting = false
                    onSkip()
                },
                Modifier.padding(start = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text(stringResource(R.string.skip)) }
        }
    }
}

/** Date, start time and duration of a workout that already happened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoggedTimeFields(s: Session, onChange: (Session) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(s.startMs).atZone(zone)
    var picking by remember { mutableStateOf(false) }

    SurfaceCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
