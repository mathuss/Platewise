package com.countmymacros.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
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
import com.countmymacros.data.Entry
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.ui.theme.MacroColors
import com.countmymacros.ui.theme.macroTextColors
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DiaryScreen(vm: AppViewModel, onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    val byMeal = vm.entries.groupBy { it.meal }
    val total = vm.entries.fold(Macros.ZERO) { acc, e -> acc + e.macros }
    val target = vm.targets.daily
    var photoMenu by remember { mutableStateOf(false) }
    val photos = rememberPhotoSource(
        onJpeg = { vm.analyzePhoto(it, vm.mealForNow()) },
        onError = { vm.toast = it },
    )

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { DateBar(vm.date, vm::setDay) }
            if (!vm.profileSet) {
                item {
                    Card(
                        onClick = onOpenProfile,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Tune, null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(stringResource(R.string.setup_goals_title), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.setup_goals_body), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item { SummaryCard(total, target) }
            items(Meal.entries.toList(), key = { it.name }) { meal ->
                MealCard(
                    meal = meal,
                    entries = byMeal[meal].orEmpty(),
                    onAdd = { vm.push(Screen.AddFood(meal)) },
                    onCopyYesterday = { vm.copyFromYesterday(meal) },
                    onEntry = { e -> vm.sheet = FoodSheet(e.asFood(), e.meal, e) },
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                onClick = { photoMenu = true },
                icon = { Icon(Icons.Filled.CameraAlt, null) },
                text = { Text(stringResource(R.string.food_photo)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.take_photo)) },
                    leadingIcon = { Icon(Icons.Filled.CameraAlt, null) },
                    onClick = { photoMenu = false; photos.takePhoto() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pick_gallery)) },
                    leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                    onClick = { photoMenu = false; photos.pickFromGallery() },
                )
            }
        }
    }
}

@Composable
private fun DateBar(date: LocalDate, onDate: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDate(date.minusDays(1)) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_day))
        }
        Column(
            Modifier.weight(1f).clickable(onClick = { onDate(LocalDate.now()) }),
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
        IconButton(onClick = { onDate(date.plusDays(1)) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
        }
    }
}

@Composable
private fun SummaryCard(total: Macros, target: Macros) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CalorieRing(total.kcal, target.kcal)
                Text(
                    stringResource(R.string.kcal_of, total.kcal.roundToInt(), target.kcal.roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            val (proteinText, carbsText, fatText) = macroTextColors()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroBar(stringResource(R.string.protein), total.protein, target.protein, MacroColors.Protein, proteinText)
                MacroBar(stringResource(R.string.carbs), total.carbs, target.carbs, MacroColors.Carbs, carbsText)
                MacroBar(stringResource(R.string.fat), total.fat, target.fat, MacroColors.Fat, fatText)
            }
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
    OutlinedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(mealName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (entries.isNotEmpty()) {
                        Text(
                            stringResource(R.string.kcal_value, sum.kcal.roundToInt()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
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
                FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_to_meal, mealName))
                }
            }
            if (entries.isNotEmpty()) {
                entries.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(end = 8.dp))
                    EntryRow(e) { onEntry(e) }
                }
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
            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(amountLabel(e), e.brand, macroSummary(e.macros)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("${e.macros.kcal.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
    }
}
