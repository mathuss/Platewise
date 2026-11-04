package com.countmymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** Short numeric cell: keeps what's being typed ("7,") while the model holds the parsed value. */
@Composable
fun NumCell(value: Double, onValue: (Double) -> Unit, modifier: Modifier = Modifier, placeholder: String = "0", pad: Int = 0) {
    var text by remember { mutableStateOf(shown(value, pad)) }
    if ((parseNum(text) ?: 0.0) != value) text = shown(value, pad)
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() || it == '.' || it == ',' }
            onValue(parseNum(text) ?: 0.0)
        },
        placeholder = { Text(placeholder, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

/**
 * One exercise with its sets, as in Hevy: a header, a row per set (type, the fields its type
 * records, optionally a done tick) and "add set". [previous] shows what was done last time as hints.
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
    val markDone = stringResource(R.string.mark_set_done)
    OutlinedCard {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(exercise, female)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        exercise?.name(Lang.current) ?: item.exerciseId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (exercise != null) {
                        Text(
                            stringResource(exercise.muscle.label) + " · " + stringResource(exercise.equipment.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rest_label), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                NumCell(item.restSeconds.toDouble(), { onChange(item.copy(restSeconds = it.roundToInt())) }, Modifier.width(96.dp))
                Text("  s", style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.set_label), Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium)
                fields.forEach {
                    Text(
                        it.label(), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showDone) Spacer(Modifier.width(44.dp))
                Spacer(Modifier.width(36.dp))
            }

            item.sets.forEachIndexed { i, set ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SetBadge(set.type, i + 1) { onChange(item.copy(sets = item.sets.replaced(i, set.copy(type = set.type.next())))) }
                    fields.forEach { f ->
                        val hint = previous.getOrNull(i)?.get(f)?.let { shown(it) }?.ifEmpty { null } ?: "0"
                        NumCell(
                            set.get(f),
                            { onChange(item.copy(sets = item.sets.replaced(i, set.with(f, it)))) },
                            Modifier.weight(1f),
                            hint,
                        )
                    }
                    if (showDone) {
                        Checkbox(
                            checked = set.done,
                            onCheckedChange = { onChange(item.copy(sets = item.sets.replaced(i, set.copy(done = it)))) },
                            modifier = Modifier.size(38.dp).semantics { contentDescription = markDone },
                        )
                    }
                    IconButton(onClick = { onChange(item.copy(sets = item.sets.filterIndexed { j, _ -> j != i })) }, Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(18.dp))
                    }
                }
            }

            TextButton(onClick = { onChange(item.copy(sets = item.sets + (item.sets.lastOrNull()?.copy(done = false) ?: WorkoutSet()))) }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text("  " + stringResource(R.string.add_set))
            }
        }
    }
}

internal fun <T> List<T>.replaced(i: Int, v: T) = mapIndexed { j, x -> if (j == i) v else x }

/** The list with the element at [from] moved to position [to]. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

/** The set number, or W / D / F for warm-up, drop set and failure; tap to cycle the type. */
@Composable
private fun SetBadge(type: SetType, number: Int, onClick: () -> Unit) {
    val (text, color) = when (type) {
        SetType.NORMAL -> number.toString() to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> "W" to MaterialTheme.colorScheme.tertiary
        SetType.DROPSET -> "D" to MaterialTheme.colorScheme.primary
        SetType.FAILURE -> "F" to MaterialTheme.colorScheme.error
    }
    val label = stringResource(type.label)
    Box(
        Modifier.size(38.dp).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold)
    }
}
