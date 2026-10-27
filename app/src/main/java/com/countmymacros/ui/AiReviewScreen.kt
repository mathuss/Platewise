package com.countmymacros.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReviewScreen(vm: AppViewModel, initialMeal: Meal) {
    var meal by remember { mutableStateOf(initialMeal) }
    var note by remember { mutableStateOf("") }
    val image = vm.aiImage
    val bitmap = remember(image) { image?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val selected = vm.aiItems.filter { it.selected }
    val total = selected.fold(Macros.ZERO) { acc, it -> acc + it.macros }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (image != null) R.string.food_photo else R.string.title_description)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            if (vm.aiItems.isNotEmpty() && !vm.aiLoading) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.meal_label), style = MaterialTheme.typography.labelLarge)
                            MealPicker(meal, { meal = it })
                        }
                        Button(
                            onClick = { vm.addReviewed(meal) },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.add_n_to, selected.size,
                                    selected.size, stringResource(meal.label), total.kcal.roundToInt(),
                                )
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (bitmap != null) {
                    Image(
                        bitmap,
                        contentDescription = stringResource(R.string.photo_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                    )
                } else if (vm.aiText != null) {
                    Card {
                        Text("“${vm.aiText}”", fontStyle = FontStyle.Italic, modifier = Modifier.padding(16.dp))
                    }
                }
            }

            val error = vm.aiError
            when {
                vm.aiLoading -> item {
                    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(R.string.analyzing), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.analyzing_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                error != null -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(error.text(), color = MaterialTheme.colorScheme.onErrorContainer)
                            if (vm.hasApiKey) {
                                OutlinedButton(onClick = { vm.reanalyze(note) }, modifier = Modifier.padding(top = 8.dp)) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (vm.aiNotes.isNotBlank()) item {
                        Text(
                            vm.aiNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(vm.aiItems) { i, item ->
                        ReviewItemCard(item) { vm.aiItems[i] = it }
                    }
                    if (vm.aiItems.isNotEmpty()) item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.selected_total), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    stringResource(R.string.kcal_value, total.kcal.roundToInt()),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                MacroLine(total)
                            }
                        }
                    }
                }
            }

            if (!vm.aiLoading && vm.hasApiKey) item {
                Column {
                    Text(
                        stringResource(R.string.correct_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = { Text(stringResource(R.string.correct_placeholder)) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.reanalyze(note) }) { Icon(Icons.Filled.Refresh, stringResource(R.string.reanalyze)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewItemCard(item: ReviewItem, onChange: (ReviewItem) -> Unit) {
    var gramsText by remember(item.name) { mutableStateOf(fmt(item.grams)) }
    Card {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.selected, onCheckedChange = { onChange(item.copy(selected = it)) })
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.kcal_value, item.macros.kcal.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                MacroLine(item.macros)
                if (item.confidence == "baja") {
                    Text(
                        stringResource(R.string.low_confidence),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            NumberField(
                value = gramsText,
                onValue = { s ->
                    gramsText = s
                    parseNum(s)?.let { g -> onChange(item.copy(grams = g)) }
                },
                label = stringResource(R.string.portion),
                suffix = "g",
                modifier = Modifier.width(112.dp),
            )
        }
    }
}
