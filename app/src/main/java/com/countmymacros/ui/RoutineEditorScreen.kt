package com.countmymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Sex

/** Create or edit a routine: a name, notes and its exercises with their planned sets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(vm: AppViewModel) {
    val draft = vm.routineDraft
    if (draft == null) {
        LaunchedEffect(Unit) { vm.pop() }
        return
    }
    val female = vm.profile.sex == Sex.FEMALE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (draft.id == 0L) R.string.new_routine else R.string.edit)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = vm::saveRoutine, enabled = draft.name.isNotBlank()) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { vm.updateRoutine(draft.copy(name = it)) },
                    label = { Text(stringResource(R.string.routine_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { vm.updateRoutine(draft.copy(notes = it)) },
                    label = { Text(stringResource(R.string.routine_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            itemsIndexed(draft.exercises) { i, item ->
                val last = draft.exercises.lastIndex
                ExerciseCard(
                    exercise = vm.exercise(item.exerciseId),
                    item = item,
                    previous = vm.previous[item.exerciseId].orEmpty(),
                    showDone = false,
                    female = female,
                    onChange = { vm.updateRoutine(draft.copy(exercises = draft.exercises.replaced(i, it))) },
                    onRemove = { vm.updateRoutine(draft.copy(exercises = draft.exercises.filterIndexed { j, _ -> j != i })) },
                    onMoveUp = if (i > 0) ({ vm.updateRoutine(draft.copy(exercises = draft.exercises.moved(i, i - 1))) }) else null,
                    onMoveDown = if (i < last) ({ vm.updateRoutine(draft.copy(exercises = draft.exercises.moved(i, i + 1))) }) else null,
                )
            }
            item {
                OutlinedButton(onClick = { vm.pickExercises(PickerTarget.ROUTINE) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("  " + stringResource(R.string.add_exercises))
                }
            }
        }
    }
}
