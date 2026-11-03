package com.countmymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.countmymacros.data.Equipment
import com.countmymacros.data.Exercise
import com.countmymacros.data.ExerciseCatalog
import com.countmymacros.data.ExerciseType
import com.countmymacros.data.Lang
import com.countmymacros.data.Muscle
import com.countmymacros.data.Sex

/** Pick exercises for the routine or workout being built: search, filter by muscle / equipment, tick several. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }
    var creating by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val female = vm.profile.sex == Sex.FEMALE
    val results = remember(vm.exercises, query, muscle, equipment) {
        ExerciseCatalog.search(vm.exercises, query, muscle, equipment)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_exercises)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { vm.addExercises(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text(stringResource(R.string.add_exercises_n, selected.size)) }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.exercise_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterMenu(
                    current = muscle?.let { stringResource(it.label) } ?: stringResource(R.string.any_muscle),
                    all = stringResource(R.string.any_muscle),
                    options = Muscle.entries.map { it to stringResource(it.label) },
                    onPick = { muscle = it },
                    modifier = Modifier.weight(1f),
                )
                FilterMenu(
                    current = equipment?.let { stringResource(it.label) } ?: stringResource(R.string.any_equipment),
                    all = stringResource(R.string.any_equipment),
                    options = Equipment.entries.map { it to stringResource(it.label) },
                    onPick = { equipment = it },
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { creating = true }, Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.create_exercise))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.id }) { e ->
                    val checked = e.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selected.remove(e.id) else selected.add(e.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseThumb(e, female, size = 56.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(e.name(Lang.current), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(e.muscle.label) + " · " + stringResource(e.equipment.label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                if (results.isEmpty()) item {
                    Text(
                        stringResource(R.string.no_exercises_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (creating) {
        CreateExerciseDialog(onDismiss = { creating = false }) { name, type, m, eq ->
            creating = false
            vm.createExercise(name, type, m, eq) { selected.add(it.id) }
        }
    }
}

@Composable
private fun <T> FilterMenu(
    current: String,
    all: String,
    options: List<Pair<T, String>>,
    onPick: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text(current, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(all) }, onClick = { open = false; onPick(null) })
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

@Composable
private fun CreateExerciseDialog(onDismiss: () -> Unit, onCreate: (String, ExerciseType, Muscle, Equipment) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ExerciseType.WEIGHT_REPS) }
    var muscle by remember { mutableStateOf(Muscle.OTHER) }
    var equipment by remember { mutableStateOf(Equipment.NONE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                FilterMenu(
                    current = type.id.replace('_', ' '),
                    all = type.id.replace('_', ' '),
                    options = ExerciseType.entries.map { it to it.id.replace('_', ' ') },
                    onPick = { type = it ?: type },
                )
                FilterMenu(
                    current = stringResource(muscle.label),
                    all = stringResource(muscle.label),
                    options = Muscle.entries.map { it to stringResource(it.label) },
                    onPick = { muscle = it ?: muscle },
                )
                FilterMenu(
                    current = stringResource(equipment.label),
                    all = stringResource(equipment.label),
                    options = Equipment.entries.map { it to stringResource(it.label) },
                    onPick = { equipment = it ?: equipment },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, type, muscle, equipment) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
