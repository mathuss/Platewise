package com.countmymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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

/**
 * Pick exercises for the routine or workout being built: search, filter by muscle / equipment
 * with chips, and tick several. The ones you did lately are listed first.
 */
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
    // Chips in alphabetical order of the label the user reads, not of the internal id.
    val muscles = Muscle.entries.map { it to stringResource(it.label) }.sortedBy { it.second }
    val equipments = Equipment.entries.map { it to stringResource(it.label) }.sortedBy { it.second }
    val recents = remember(vm.recentWorkouts, vm.exercises) {
        vm.recentWorkouts.flatMap { w -> w.exercises.map { it.exerciseId } }.distinct().take(6).mapNotNull { vm.exercise(it) }
    }
    val filtering = query.isNotBlank() || muscle != null || equipment != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_exercises)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = { creating = true }) { Text("+ " + stringResource(R.string.create_short)) }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        if (selected.isNotEmpty()) {
                            Text(
                                pluralStringResource(R.plurals.exercises_selected, selected.size, selected.size),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                selected.mapNotNull { vm.exercise(it)?.name(Lang.current) }.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Button(onClick = { vm.addExercises(selected.toList()) }, enabled = selected.isNotEmpty()) {
                        Text(stringResource(R.string.add))
                    }
                }
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
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = muscle == null, onClick = { muscle = null }, label = { Text(stringResource(R.string.any_muscle)) })
                muscles.forEach { (m, label) ->
                    FilterChip(selected = muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(label) })
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = equipment == null, onClick = { equipment = null }, label = { Text(stringResource(R.string.any_equipment)) })
                equipments.forEach { (e, label) ->
                    FilterChip(selected = equipment == e, onClick = { equipment = if (equipment == e) null else e }, label = { Text(label) })
                }
            }
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                if (!filtering && recents.isNotEmpty()) {
                    item { PickerHeader(stringResource(R.string.recent_exercises)) }
                    items(recents, key = { "r" + it.id }) { e -> PickerRow(e, female, e.id in selected) { toggle(selected, e.id) } }
                }
                if (!filtering) item { PickerHeader(stringResource(R.string.all_exercises_n, results.size)) }
                items(results, key = { it.id }) { e -> PickerRow(e, female, e.id in selected) { toggle(selected, e.id) } }
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

private fun toggle(selected: MutableList<String>, id: String) {
    if (id in selected) selected.remove(id) else selected.add(id)
}

@Composable
private fun PickerHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(e: Exercise, female: Boolean, checked: Boolean, onClick: () -> Unit) {
    var zoom by remember { mutableStateOf(false) }
    if (zoom) ExercisePhotoDialog(e, female) { zoom = false }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumb(e, female, size = 56.dp, onClick = { zoom = true })
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.name(Lang.current), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip(stringResource(e.muscle.label), tone = TagTone.PRIMARY)
                TagChip(stringResource(e.equipment.label))
            }
        }
        AddCircleButton(checked, onClick, stringResource(R.string.add_exercises))
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
                    current = stringResource(type.label),
                    all = stringResource(type.label),
                    options = ExerciseType.entries.map { it to stringResource(it.label) },
                    onPick = { type = it ?: type },
                )
                FilterMenu(
                    current = stringResource(muscle.label),
                    all = stringResource(muscle.label),
                    options = Muscle.entries.map { it to stringResource(it.label) }.sortedBy { it.second },
                    onPick = { muscle = it ?: muscle },
                )
                FilterMenu(
                    current = stringResource(equipment.label),
                    all = stringResource(equipment.label),
                    options = Equipment.entries.map { it to stringResource(it.label) }.sortedBy { it.second },
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
