package com.countmymacros.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countmymacros.R
import com.countmymacros.data.Exercise
import com.countmymacros.data.Lang
import com.countmymacros.data.SetField
import com.countmymacros.data.SetType
import com.countmymacros.data.WorkoutExercise
import com.countmymacros.data.WorkoutSet
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import kotlin.math.roundToInt

/** [pad] > 0 shows a whole number with leading zeros (minutes: "04"), zero included. */
private fun shown(v: Double, pad: Int = 0) =
    if (pad > 0) v.roundToInt().toString().padStart(pad, '0') else if (v == 0.0) "" else fmt(v)

/** A filled number cell without a border: keeps what's being typed ("7,") while the model holds the parsed value. */
@Composable
fun NumCell(
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    pad: Int = 0,
    emphasized: Boolean = false,
) {
    var text by remember { mutableStateOf(shown(value, pad)) }
    if ((parseNum(text) ?: 0.0) != value) text = shown(value, pad)
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() || it == '.' || it == ',' }
            onValue(parseNum(text) ?: 0.0)
        },
        textStyle = TextStyle(
            color = if (emphasized) cs.primary else cs.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty()) Text(placeholder, color = cs.onSurfaceVariant, fontSize = 15.sp)
                inner()
            }
        },
        modifier = modifier,
    )
}

private fun SetField.label() = when (this) {
    SetField.KG -> "kg"
    SetField.REPS -> "reps"
    SetField.DURATION -> "min"
    SetField.DISTANCE -> "km"
}

private fun WorkoutSet.get(f: SetField) = when (f) {
    SetField.KG -> kg
    SetField.REPS -> reps.toDouble()
    SetField.DURATION -> durationS / 60.0
    SetField.DISTANCE -> distanceM / 1000.0
}

private fun WorkoutSet.with(f: SetField, v: Double) = when (f) {
    SetField.KG -> copy(kg = v)
    SetField.REPS -> copy(reps = v.roundToInt())
    SetField.DURATION -> copy(durationS = (v * 60).roundToInt())
    SetField.DISTANCE -> copy(distanceM = v * 1000)
}

/** "80 kg · 8 reps" for the fields a set of this exercise has. */
fun WorkoutSet.describe(fields: List<SetField>): String =
    fields.joinToString(" · ") { "${fmt(get(it))} ${it.label()}" }

/** "60×8": what was done last time, short enough for a narrow column. A set ticked with nothing typed shows "–". */
private fun WorkoutSet.compact(fields: List<SetField>): String =
    if (fields.all { get(it) == 0.0 }) "–" else fields.joinToString("×") { fmt(get(it)) }

/**
 * One exercise with its sets: a header (photo, name, muscle, rest), a row per set (type, what was
 * done last time, the fields its type records, a done tick) and "add set". The first set not yet
 * done is outlined, the done ones are tinted.
 */
@Composable
fun ExerciseCard(
    exercise: Exercise?,
    item: WorkoutExercise,
    previous: List<WorkoutSet>,
    showDone: Boolean,
    female: Boolean,
    onChange: (WorkoutExercise) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val fields = exercise?.type?.fields ?: listOf(SetField.KG, SetField.REPS)
    var menu by remember { mutableStateOf(false) }
    var restDialog by remember { mutableStateOf(false) }
    val markDone = stringResource(R.string.mark_set_done)
    val current = if (showDone) item.sets.indexOfFirst { !it.done } else -1

    SurfaceCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(exercise, female)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        exercise?.name(Lang.current) ?: item.exerciseId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (exercise != null) TagChip(stringResource(exercise.muscle.label), tone = TagTone.PRIMARY)
                        TagChip(
                            "${item.restSeconds} s",
                            icon = Icons.Filled.Timer,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { restDialog = true },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        onMoveUp?.let { DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; it() }) }
                        onMoveDown?.let { DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; it() }) }
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menu = false; onRemove() })
                    }
                }
            }

            val header = MaterialTheme.typography.labelSmall
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.set_label).uppercase(), Modifier.width(34.dp), style = header, color = muted, textAlign = TextAlign.Center, maxLines = 1)
                if (showDone) Text(stringResource(R.string.previous).uppercase(), Modifier.weight(1f), style = header, color = muted, textAlign = TextAlign.Center, maxLines = 1)
                fields.forEach { Text(it.label().uppercase(), Modifier.weight(1f), style = header, color = muted, textAlign = TextAlign.Center) }
                if (showDone) Box(Modifier.width(36.dp))
                Box(Modifier.width(28.dp))
            }

            item.sets.forEachIndexed { i, set ->
                val cs = MaterialTheme.colorScheme
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (set.done) cs.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (i == current) Modifier.border(BorderStroke(1.5.dp, cs.primary), RoundedCornerShape(14.dp)) else Modifier)
                        .padding(horizontal = 2.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SetBadge(set.type, i + 1, highlight = i == current) {
                        onChange(item.copy(sets = item.sets.replaced(i, set.copy(type = set.type.next()))))
                    }
                    if (showDone) {
                        Text(
                            previous.getOrNull(i)?.compact(fields) ?: "–",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    fields.forEach { f ->
                        NumCell(
                            set.get(f),
                            { onChange(item.copy(sets = item.sets.replaced(i, set.with(f, it)))) },
                            Modifier.weight(1f),
                            placeholder = "—",
                            emphasized = set.done,
                        )
                    }
                    if (showDone) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (set.done) cs.primary else cs.surfaceContainerHigh)
                                .clickable { onChange(item.copy(sets = item.sets.replaced(i, set.copy(done = !set.done)))) }
                                .semantics { contentDescription = markDone },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check, null, Modifier.size(20.dp),
                                tint = if (set.done) cs.onPrimary else muted.copy(alpha = 0.4f),
                            )
                        }
                    }
                    IconButton(onClick = { onChange(item.copy(sets = item.sets.filterIndexed { j, _ -> j != i })) }, Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(16.dp), tint = muted)
                    }
                }
            }

            TextButton(
                onClick = { onChange(item.copy(sets = item.sets + (item.sets.lastOrNull()?.copy(done = false) ?: WorkoutSet()))) },
                Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text("  " + stringResource(R.string.add_set))
            }
        }
    }

    if (restDialog) {
        AlertDialog(
            onDismissRequest = { restDialog = false },
            title = { Text(stringResource(R.string.rest_label)) },
            text = {
                NumberField(
                    item.restSeconds.toString(),
                    { s -> s.toIntOrNull()?.let { onChange(item.copy(restSeconds = it.coerceIn(0, 3600))) } },
                    stringResource(R.string.rest_label),
                    Modifier.fillMaxWidth(),
                    "s",
                )
            },
            confirmButton = { TextButton(onClick = { restDialog = false }) { Text(stringResource(R.string.done_action)) } },
        )
    }
}

internal fun <T> List<T>.replaced(i: Int, v: T) = mapIndexed { j, x -> if (j == i) v else x }

/** The list with the element at [from] moved to position [to]. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

/** The set number, or W / D / F for warm-up, drop set and failure; tap to cycle the type. */
@Composable
private fun SetBadge(type: SetType, number: Int, highlight: Boolean, onClick: () -> Unit) {
    val (text, color) = when (type) {
        SetType.NORMAL -> number.toString() to if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> "W" to MaterialTheme.colorScheme.tertiary
        SetType.DROPSET -> "D" to MaterialTheme.colorScheme.primary
        SetType.FAILURE -> "F" to MaterialTheme.colorScheme.error
    }
    val label = stringResource(type.label)
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold)
    }
}
