package com.countmymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Entry
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.ui.theme.MacroColors
import com.countmymacros.ui.theme.macroTextColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun DiaryScreen(
    vm: AppViewModel,
    onOpenProfile: () -> Unit,
    onOpenTrain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val byMeal = vm.entries.groupBy { it.meal }
    val total = vm.entries.fold(Macros.ZERO) { acc, e -> acc + e.macros }
    val target = vm.targets.daily

    // The dots under the week strip: days of this week with something logged.
    val monday = vm.date.minusDays((vm.date.dayOfWeek.value - 1).toLong())
    @Suppress("ProduceStateDoesNotAssignValue")
    val logged by produceState(emptySet<LocalDate>(), monday, vm.entries) {
        value = withContext(Dispatchers.IO) {
            vm.repo.range(monday, monday.plusDays(6)).map { LocalDate.parse(it.date) }.toSet()
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        state = vm.diaryList,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DateBar(vm.date, vm.today, vm::setDay) }
        item { DayStrip(vm.date, vm::setDay, marked = logged) }
        if (!vm.profileSet) {
            item {
                Surface(
                    onClick = onOpenProfile,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tune, null, Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.setup_goals_title),
                            Modifier.weight(1f).padding(start = 10.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }
        item { SummaryCard(total, target) }
        if (vm.dayWorkouts.isNotEmpty()) {
            item { WorkoutTile(vm, onOpenTrain) }
        }
        items(Meal.entries.toList(), key = { it.name }) { meal ->
            MealCard(
                meal = meal,
                entries = byMeal[meal].orEmpty(),
                onAdd = { vm.push(Screen.AddFood(meal)) },
                onCopyYesterday = { vm.copyFromYesterday(meal) },
                onEntry = { e -> vm.sheet = FoodSheet(e.asFood(), e.meal, e) },
            )
        }
    }
}

/** Previous / next day, tap the date to pick any day, and a shortcut back to today when elsewhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateBar(date: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDate(date.minusDays(1)) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_day))
        }
        Column(
            Modifier.weight(1f).clickable { picking = true },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(dayLabel(date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (isRelativeDay(date)) {
                Text(
                    fullDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (date != today) TextButton(onClick = { onDate(today) }) { Text(stringResource(R.string.today)) }
        IconButton(onClick = { onDate(date.plusDays(1)) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
        }
    }
    if (picking) {
        // The picker works in UTC midnights; convert from / to the local date.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

/** The day at a glance: a big ring with what is left, and the three macros in their colors. */
@Composable
private fun SummaryCard(total: Macros, target: Macros) {
    HeroCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalorieRing(total.kcal, target.kcal, size = 124.dp)
                Spacer(Modifier.width(16.dp))
                val (proteinText, carbsText, fatText) = macroTextColors()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroBar(stringResource(R.string.protein), total.protein, target.protein, MacroColors.Protein, proteinText)
                    MacroBar(stringResource(R.string.carbs), total.carbs, target.carbs, MacroColors.Carbs, carbsText)
                    MacroBar(stringResource(R.string.fat), total.fat, target.fat, MacroColors.Fat, fatText)
                }
            }
            Text(
                stringResource(R.string.kcal_of, total.kcal.roundToInt(), target.kcal.roundToInt()),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Today's training, shown as information (it does not change the budget); tapping opens Train. */
@Composable
private fun WorkoutTile(vm: AppViewModel, onOpen: () -> Unit) {
    val names = vm.dayWorkouts.joinToString(", ") { it.name }
    val minutes = vm.dayWorkouts.sumOf { it.durationMin }
    SurfaceCard(onClick = onOpen) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.LocalFireDepartment, warm = true)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(names, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$minutes min · " + stringResource(R.string.burned_today, vm.burned.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<Entry>,
    onAdd: () -> Unit,
    onCopyYesterday: () -> Unit,
    onEntry: (Entry) -> Unit,
) {
    val sum = entries.fold(Macros.ZERO) { acc, e -> acc + e.macros }
    val mealName = stringResource(meal.label)
    var menu by remember { mutableStateOf(false) }
    SurfaceCard {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(mealIcon(meal), size = 34.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        mealName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (entries.isNotEmpty()) MacroLine(sum, whole = true)
                }
                if (entries.isNotEmpty()) {
                    Text(
                        stringResource(R.string.kcal_value, sum.kcal.roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_yesterday)) },
                            onClick = { menu = false; onCopyYesterday() },
                        )
                    }
                }
            }
            entries.forEach { e ->
                HorizontalDivider(Modifier.padding(end = 8.dp))
                EntryRow(e) { onEntry(e) }
            }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(vertical = 10.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.add_to_meal, mealName),
                    Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EntryRow(e: Entry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                listOf(amountLabel(e), e.brand).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MacroLine(e.macros, whole = true)
        }
        Text("${e.macros.kcal.roundToInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
