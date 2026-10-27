package com.countmymacros.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Activity as ActivityLevel
import com.countmymacros.data.GoalType
import com.countmymacros.data.Goals
import com.countmymacros.data.MacroPreset
import com.countmymacros.data.Macros
import com.countmymacros.data.Sex
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(stringResource(R.string.daily_goal), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.kcal_value, t.daily.kcal.roundToInt()),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                MacroLine(t.daily)
                if (p.preset != MacroPreset.CUSTOM) {
                    Text(
                        stringResource(R.string.bmr_line, t.bmr.roundToInt(), t.tdee.roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        SectionTitle(stringResource(R.string.about_you))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = p.sex == s,
                    onClick = { vm.saveProfile(p.copy(sex = s)) },
                    shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                ) { Text(stringResource(s.label)) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(age, { s ->
                age = s
                s.toIntOrNull()?.takeIf { it in 10..110 }?.let { vm.saveProfile(p.copy(age = it)) }
            }, stringResource(R.string.age), Modifier.weight(1f), stringResource(R.string.years))
            NumberField(height, { s ->
                height = s
                parseNum(s)?.takeIf { it in 100.0..250.0 }?.let { vm.saveProfile(p.copy(heightCm = it)) }
            }, stringResource(R.string.height), Modifier.weight(1f), "cm")
            NumberField(weight, { s ->
                weight = s
                parseNum(s)?.takeIf { it in 25.0..400.0 }?.let { vm.saveProfile(p.copy(weightKg = it)) }
            }, stringResource(R.string.weight), Modifier.weight(1f), "kg")
        }

        SectionTitle(stringResource(R.string.activity_title))
        Picker(
            current = "${stringResource(p.activity.label)} — ${stringResource(p.activity.detail)}",
            options = ActivityLevel.entries.map { "${stringResource(it.label)} — ${stringResource(it.detail)}" },
        ) { vm.saveProfile(p.copy(activity = ActivityLevel.entries[it])) }

        SectionTitle(stringResource(R.string.goal_title))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GoalType.entries.forEachIndexed { i, g ->
                SegmentedButton(
                    selected = p.goal == g,
                    onClick = { vm.saveProfile(p.copy(goal = g)) },
                    shape = SegmentedButtonDefaults.itemShape(i, GoalType.entries.size),
                ) { Text(stringResource(g.label), maxLines = 1) }
            }
        }
        if (p.goal != GoalType.MAINTAIN) {
            Text(
                stringResource(
                    if (p.goal == GoalType.LOSE) R.string.deficit_line else R.string.surplus_line,
                    (p.pace * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Slider(
                value = p.pace.toFloat(),
                onValueChange = { vm.saveProfile(p.copy(pace = (it * 20).roundToInt() / 20.0)) },
                valueRange = 0.05f..0.25f,
                steps = 3,
            )
        }

        SectionTitle(stringResource(R.string.macro_split))
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
            NumberField(cKcal, { cKcal = it; vm.saveProfile(p.copy(custom = customFrom(k = it))) }, stringResource(R.string.calories), Modifier.fillMaxWidth().padding(top = 8.dp), "kcal")
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!vm.profileSet) {
            Button(onClick = { vm.saveProfile(p) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(stringResource(R.string.use_goals))
            }
        }

        SectionTitle(stringResource(R.string.language))
        LanguagePicker()

        SectionTitle(stringResource(R.string.ai_section))
        ApiKeyCard(vm)

        SectionTitle(stringResource(R.string.about))
        BrandRow()
        Text(
            stringResource(R.string.about_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.padding(bottom = 24.dp))
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
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
private fun ApiKeyCard(vm: AppViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var visible by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ai_body), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.api_key_label)) },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, stringResource(R.string.show))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveApiKey(key); vm.toast = uiText(R.string.toast_api_key_saved) },
                    enabled = key.trim() != vm.apiKey,
                ) { Text(stringResource(R.string.save)) }
                TextButton(onClick = { uri.openUri("https://console.anthropic.com/settings/keys") }) {
                    Text(stringResource(R.string.get_key))
                }
            }
            Text(
                stringResource(if (vm.hasApiKey) R.string.ai_on else R.string.ai_off),
                style = MaterialTheme.typography.labelMedium,
                color = if (vm.hasApiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
