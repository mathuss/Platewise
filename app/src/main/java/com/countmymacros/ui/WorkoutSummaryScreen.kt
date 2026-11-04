package com.countmymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Lang
import com.countmymacros.data.Records
import com.countmymacros.data.SetField
import com.countmymacros.data.SetRecord
import com.countmymacros.data.Sex
import com.countmymacros.data.Workout
import com.countmymacros.data.fmt
import java.time.LocalDate
import kotlin.math.roundToInt

/** What you just did: time, volume, sets, calories, the personal records and each exercise's best set. */
@Composable
fun WorkoutSummaryScreen(vm: AppViewModel, workout: Workout, records: List<SetRecord>) {
    val female = vm.profile.sex == Sex.FEMALE
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = vm::pop,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text(stringResource(R.string.done_action)) }
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.summary_saved).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(workout.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    dayLabel(LocalDate.parse(workout.date)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HeroCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row {
                        BigStat(workout.durationMin.toString(), "min", stringResource(R.string.summary_duration), Modifier.weight(1f))
                        BigStat(fmt(workout.volumeKg), "kg", stringResource(R.string.summary_volume), Modifier.weight(1f))
                    }
                    Row {
                        BigStat(workout.setCount.toString(), "", stringResource(R.string.summary_sets), Modifier.weight(1f))
                        BigStat(workout.kcal.roundToInt().toString(), "kcal", stringResource(R.string.summary_kcal), Modifier.weight(1f))
                    }
                }
            }

            records.forEach { r ->
                val name = vm.exercise(r.exerciseId)?.name(Lang.current) ?: r.exerciseId
                Row(
                    Modifier.fillMaxWidth()
                        .padding(vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SurfaceCard {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.EmojiEvents, warm = true)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(stringResource(R.string.summary_records), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "$name: ${fmt(r.kg)} kg × ${r.reps}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.summary_exercises).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            workout.exercises.forEach { e ->
                val exercise = vm.exercise(e.exerciseId)
                val best = Records.best(e.sets)
                SurfaceCard {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(exercise, female, size = 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(exercise?.name(Lang.current) ?: e.exerciseId, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            val fields = exercise?.type?.fields ?: listOf(SetField.KG, SetField.REPS)
                            Text(
                                pluralStringResource(R.plurals.sets_n, e.sets.size, e.sets.size) +
                                    (best?.let { " · " + stringResource(R.string.best_set, it.describe(fields)) } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BigStat(value: String, unit: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
