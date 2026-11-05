package com.countmymacros.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Activity as ActivityLevel
import com.countmymacros.data.GoalType
import com.countmymacros.data.Goals
import com.countmymacros.data.MacroPreset
import com.countmymacros.data.Macros
import com.countmymacros.data.Profile
import com.countmymacros.data.Sex
import com.countmymacros.data.Targets
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import com.countmymacros.ui.theme.MacroColors
import com.countmymacros.ui.theme.macroTextColors
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = vm.profile
    val t = Goals.targets(p)
    // Text fields keep their own strings so partial input ("7", "7,") isn't fought by re-formatting.
    var age by remember { mutableStateOf(p.age.toString()) }
    var height by remember { mutableStateOf(fmt(p.heightCm)) }
    var weight by remember { mutableStateOf(fmt(p.weightKg)) }
    var editing by remember { mutableStateOf<String?>(null) }
    var cKcal by remember { mutableStateOf(fmt(p.custom.kcal)) }
    var cP by remember { mutableStateOf(fmt(p.custom.protein)) }
    var cC by remember { mutableStateOf(fmt(p.custom.carbs)) }
    var cF by remember { mutableStateOf(fmt(p.custom.fat)) }

    fun customFrom(k: String = cKcal, pr: String = cP, ca: String = cC, fa: String = cF) = Macros(
        parseNum(k) ?: 0.0, parseNum(pr) ?: 0.0, parseNum(ca) ?: 0.0, parseNum(fa) ?: 0.0,
    )

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(vm.profileScroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GoalHero(p, t)

        ProfileSection(Icons.Filled.Person, stringResource(R.string.about_you)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { s ->
                    ChoiceTile(
                        if (s == Sex.MALE) Icons.Filled.Male else Icons.Filled.Female,
                        stringResource(s.label),
                        selected = p.sex == s,
                        onClick = { vm.saveProfile(p.copy(sex = s)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(Icons.Filled.Cake, p.age.toString(), stringResource(R.string.age), Modifier.weight(1f)) {
                    age = p.age.toString(); editing = "age"
                }
                MetricTile(Icons.Filled.Height, fmt(p.heightCm), stringResource(R.string.height) + " (cm)", Modifier.weight(1f)) {
                    height = fmt(p.heightCm); editing = "height"
                }
                MetricTile(Icons.Filled.MonitorWeight, fmt(p.weightKg), stringResource(R.string.weight) + " (kg)", Modifier.weight(1f)) {
                    weight = fmt(p.weightKg); editing = "weight"
                }
            }
            BmiGauge(p)
        }
        when (editing) {
            "age" -> EditDialog(stringResource(R.string.age), onClose = { editing = null; age = p.age.toString() }) {
                NumberField(age, { v ->
                    age = v
                    v.toIntOrNull()?.takeIf { it in 10..110 }?.let { vm.saveProfile(p.copy(age = it)) }
                }, stringResource(R.string.age), Modifier.fillMaxWidth(), stringResource(R.string.years))
            }
            "height" -> EditDialog(stringResource(R.string.height), onClose = { editing = null; height = fmt(p.heightCm) }) {
                NumberField(height, { v ->
                    height = v
                    parseNum(v)?.takeIf { it in 100.0..250.0 }?.let { vm.saveProfile(p.copy(heightCm = it)) }
                }, stringResource(R.string.height), Modifier.fillMaxWidth(), "cm")
            }
            "weight" -> EditDialog(stringResource(R.string.weight), onClose = { editing = null; weight = fmt(p.weightKg) }) {
                NumberField(weight, { v ->
                    weight = v
                    parseNum(v)?.takeIf { it in 25.0..400.0 }?.let { vm.saveProfile(p.copy(weightKg = it)) }
                }, stringResource(R.string.weight), Modifier.fillMaxWidth(), "kg")
            }
        }

        ProfileSection(Icons.AutoMirrored.Filled.DirectionsRun, stringResource(R.string.activity_title)) {
            ActivityLevel.entries.forEach { a ->
                ActivityOption(a, a == p.activity) { vm.saveProfile(p.copy(activity = a)) }
            }
            Text(
                stringResource(R.string.activity_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ProfileSection(Icons.Filled.Flag, stringResource(R.string.goal_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalType.entries.forEach { g ->
                    ChoiceTile(
                        goalIcon(g), stringResource(g.shortLabel),
                        selected = p.goal == g,
                        onClick = { vm.saveProfile(p.copy(goal = g)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (p.goal != GoalType.MAINTAIN) {
                val delta = (t.daily.kcal - t.tdee).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            if (p.goal == GoalType.LOSE) R.string.deficit_line else R.string.surplus_line,
                            (p.pace * 100).roundToInt(),
                        ),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TagChip(
                        stringResource(R.string.pace_delta, (if (delta > 0) "+" else "") + delta),
                        tone = if (p.goal == GoalType.LOSE) TagTone.WARM else TagTone.PRIMARY,
                    )
                }
                Slider(
                    value = p.pace.toFloat(),
                    onValueChange = { vm.saveProfile(p.copy(pace = (it * 20).roundToInt() / 20.0)) },
                    valueRange = 0.05f..0.25f,
                    steps = 3,
                )
            }
        }

        ProfileSection(Icons.Filled.PieChart, stringResource(R.string.macro_split)) {
            SplitBar(t.daily)
            Picker(
                current = presetLabel(p.preset),
                options = MacroPreset.entries.map { presetLabel(it) },
            ) { i ->
                val preset = MacroPreset.entries[i]
                // Seed custom targets from what's currently prescribed so it's a starting point, not zeros.
                if (preset == MacroPreset.CUSTOM && p.preset != MacroPreset.CUSTOM) {
                    val d = t.daily
                    cKcal = fmt(d.kcal); cP = fmt(d.protein); cC = fmt(d.carbs); cF = fmt(d.fat)
                    vm.saveProfile(p.copy(preset = preset, custom = d))
                } else vm.saveProfile(p.copy(preset = preset))
            }
            if (p.preset == MacroPreset.CUSTOM) {
                NumberField(cKcal, { cKcal = it; vm.saveProfile(p.copy(custom = customFrom(k = it))) }, stringResource(R.string.calories), Modifier.fillMaxWidth(), "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(cP, { cP = it; vm.saveProfile(p.copy(custom = customFrom(pr = it))) }, stringResource(R.string.protein), Modifier.weight(1f), "g")
                    NumberField(cC, { cC = it; vm.saveProfile(p.copy(custom = customFrom(ca = it))) }, stringResource(R.string.carbs_short), Modifier.weight(1f), "g")
                    NumberField(cF, { cF = it; vm.saveProfile(p.copy(custom = customFrom(fa = it))) }, stringResource(R.string.fat), Modifier.weight(1f), "g")
                }
                val c = customFrom()
                val fromMacros = c.protein * 4 + c.carbs * 4 + c.fat * 9
                if (fromMacros > 0 && abs(fromMacros - c.kcal) > 50) {
                    Text(
                        stringResource(R.string.macros_sum, fromMacros.roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        if (!vm.profileSet) {
            Button(onClick = { vm.saveProfile(p) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.use_goals))
            }
        }

        ProfileSection(Icons.Filled.Language, stringResource(R.string.language)) { LanguagePicker() }

        ProfileSection(Icons.Filled.Storage, stringResource(R.string.data_title)) { DataBackup(vm) }

        ProfileSection(Icons.Filled.Info, stringResource(R.string.about)) {
            BrandRow()
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.padding(bottom = 12.dp))
    }
}

/** A card with an icon badge and a title, like the other tabs' cards, holding one group of settings. */
@Composable
private fun ProfileSection(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    SurfaceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 34.dp)
                Text(
                    title,
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

private fun goalIcon(g: GoalType): ImageVector = when (g) {
    GoalType.LOSE -> Icons.AutoMirrored.Filled.TrendingDown
    GoalType.MAINTAIN -> Icons.AutoMirrored.Filled.TrendingFlat
    GoalType.GAIN -> Icons.AutoMirrored.Filled.TrendingUp
}

private fun activityIcon(a: ActivityLevel): ImageVector = when (a) {
    ActivityLevel.SEDENTARY -> Icons.Filled.Chair
    ActivityLevel.LIGHT -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityLevel.MODERATE -> Icons.AutoMirrored.Filled.DirectionsRun
    ActivityLevel.ACTIVE -> Icons.Filled.FitnessCenter
    ActivityLevel.VERY_ACTIVE -> Icons.Filled.LocalFireDepartment
}

/** The daily goal at a glance: the macro split as a ring with the calories inside, and what each macro asks for. */
@Composable
private fun GoalHero(p: Profile, t: Targets) {
    val (proteinText, carbsText, fatText) = macroTextColors()
    val kcal = t.daily.kcal
    HeroCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.daily_goal), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TagChip(stringResource(p.goal.label), icon = goalIcon(p.goal), tone = TagTone.PRIMARY)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MacroDonut(t.daily, Modifier.size(112.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroShare(stringResource(R.string.protein), t.daily.protein, t.daily.protein * 4 / kcal, MacroColors.Protein, proteinText)
                    MacroShare(stringResource(R.string.carbs), t.daily.carbs, t.daily.carbs * 4 / kcal, MacroColors.Carbs, carbsText)
                    MacroShare(stringResource(R.string.fat), t.daily.fat, t.daily.fat * 9 / kcal, MacroColors.Fat, fatText)
                }
            }
            if (p.preset != MacroPreset.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(stringResource(R.string.bmr_chip, t.bmr.roundToInt()), icon = Icons.Filled.LocalFireDepartment)
                    TagChip(stringResource(R.string.tdee_chip, t.tdee.roundToInt()), icon = Icons.Filled.Bolt)
                }
            }
        }
    }
}

/** A macro with a colored dot, its grams and its share of the day's calories. */
@Composable
private fun MacroShare(label: String, grams: Double, share: Double, color: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            label,
            Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(fmt(grams) + " g", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(
            (share * 100).roundToInt().toString() + "%",
            Modifier.width(40.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
        )
    }
}

/** One option of a few, as a tile with an icon: filled and outlined in the accent color when chosen. */
@Composable
private fun ChoiceTile(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHigh)
            .then(if (selected) Modifier.border(BorderStroke(1.5.dp, cs.primary), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = if (selected) cs.primary else cs.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) cs.onPrimaryContainer else cs.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** A number you can tap to edit, with its icon and caption. */
@Composable
private fun MetricTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = cs.primary)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Body-mass index on a colored scale with a marker. A rough guide: it can't tell muscle from fat. */
@Composable
private fun BmiGauge(p: Profile) {
    val h = p.heightCm / 100
    if (h <= 0 || p.weightKg <= 0) return
    val bmi = p.weightKg / (h * h)
    val cs = MaterialTheme.colorScheme
    // The scale runs 15 to 40; each band is as wide as its range.
    val bands = listOf(3.5f to MacroColors.Fat, 6.5f to cs.primary, 5f to MacroColors.Carbs, 10f to cs.error)
    val (category, tone) = when {
        bmi < 18.5 -> R.string.bmi_under to TagTone.NEUTRAL
        bmi < 25 -> R.string.bmi_normal to TagTone.PRIMARY
        bmi < 30 -> R.string.bmi_over to TagTone.WARM
        else -> R.string.bmi_obese to TagTone.WARM
    }
    val position = ((bmi - 15) / 25).toFloat().coerceIn(0.02f, 0.98f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bmi_title), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Text(
                String.format(java.util.Locale.getDefault(), "%.1f", bmi),
                Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TagChip(stringResource(category), tone = tone)
        }
        Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                bands.forEach { (w, c) -> Box(Modifier.weight(w).height(8.dp).background(c.copy(alpha = 0.75f))) }
            }
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(position))
                Box(Modifier.size(16.dp).clip(CircleShape).background(cs.onSurface).border(2.dp, cs.surfaceContainerHigh, CircleShape))
                Box(Modifier.weight(1f - position))
            }
        }
        Text(stringResource(R.string.bmi_note), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

/** The calorie split between protein, carbs and fat as one stacked bar, in the macro colors. */
@Composable
private fun SplitBar(m: Macros) {
    val parts = listOf(m.protein * 4 to MacroColors.Protein, m.carbs * 4 to MacroColors.Carbs, m.fat * 9 to MacroColors.Fat)
    val sum = parts.sumOf { it.first }
    if (sum <= 0) return
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        parts.forEach { (kcal, color) ->
            if (kcal > 0) Box(Modifier.weight(kcal.toFloat()).height(14.dp).background(color))
        }
    }
}

@Composable
private fun ActivityOption(level: ActivityLevel, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.5.dp, cs.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) cs.primary else cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(activityIcon(level), null, Modifier.size(22.dp), tint = if (selected) cs.onPrimary else cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(level.label), fontWeight = FontWeight.SemiBold)
                Text(stringResource(level.detail), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = cs.primary)
        }
    }
}

/** Export everything to a file the user picks, or replace everything from one: the only copy otherwise lives on this phone. */
@Composable
private fun DataBackup(vm: AppViewModel) {
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(context.contentResolver, uri)
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }
    Text(
        stringResource(R.string.data_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { export.launch("platewise-${LocalDate.now()}.json") }, Modifier.weight(1f)) {
            Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
            Text("  " + stringResource(R.string.export_data), maxLines = 1)
        }
        OutlinedButton(onClick = { importFile.launch(arrayOf("*/*")) }, Modifier.weight(1f)) {
            Icon(Icons.Filled.Upload, null, Modifier.size(18.dp))
            Text("  " + stringResource(R.string.import_data), maxLines = 1)
        }
    }
    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            text = stringResource(R.string.import_confirm_body),
            confirm = stringResource(R.string.import_confirm),
            onConfirm = { pendingImport = null; vm.importFrom(context.contentResolver, uri) },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun presetLabel(m: MacroPreset): String =
    if (m == MacroPreset.CUSTOM) stringResource(m.label)
    else stringResource(
        R.string.preset_line,
        stringResource(m.label),
        (m.protein * 100).roundToInt(),
        (m.carbs * 100).roundToInt(),
        (m.fat * 100).roundToInt(),
    )

@Composable
private fun Picker(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current, modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}

/** System / English / Español. Language names stay in their own language, as is convention. */
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val current = remember { AppLanguage.get(context) }
    val options = listOf("" to stringResource(R.string.lang_system), "en" to "English", "es" to "Español")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = { if (tag != current) context.findActivity()?.let { AppLanguage.set(it, tag) } },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The launcher icon (its 18/108 margin scaled back up) plus the app name and version. */
@Composable
private fun BrandRow() {
    val context = LocalContext.current
    val version = remember { appVersionName(context) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(60.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// BuildConfig isn't enabled for this module, so the version comes from PackageManager.
private fun appVersionName(context: Context): String {
    val pm = context.packageManager
    val versionName = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0).versionName
    }
    return versionName ?: ""
}

@Composable
private fun EditDialog(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = content,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done_action)) } },
    )
}
