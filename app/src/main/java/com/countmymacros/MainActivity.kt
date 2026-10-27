package com.countmymacros

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.countmymacros.ui.AddFoodScreen
import com.countmymacros.ui.AiReviewScreen
import com.countmymacros.ui.AppLanguage
import com.countmymacros.ui.AppViewModel
import com.countmymacros.ui.CreateFoodScreen
import com.countmymacros.ui.DiaryScreen
import com.countmymacros.ui.FoodSheetHost
import com.countmymacros.ui.ProfileScreen
import com.countmymacros.ui.ProgressScreen
import com.countmymacros.ui.Screen
import com.countmymacros.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                App(viewModel())
            }
        }
    }
}

private enum class Tab(@StringRes val label: Int) {
    DIARY(R.string.tab_diary),
    PROGRESS(R.string.tab_progress),
    PROFILE(R.string.tab_profile),
}

@Composable
private fun App(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Clear only after it's shown: clearing first would change the key and cancel this effect.
    LaunchedEffect(vm.toast) {
        val msg = vm.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg.resolve(context))
        vm.toast = null
    }

    // Back closes pushed pages first, then returns to the diary, then exits.
    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.pop() }
    BackHandler(enabled = vm.stack.isEmpty() && tab != 0) { tab = 0 }

    when (val top = vm.stack.lastOrNull()) {
        is Screen.AddFood -> AddFoodScreen(vm, top.meal)
        is Screen.AiReview -> AiReviewScreen(vm, top.meal)
        is Screen.CreateFood -> CreateFoodScreen(vm, top.meal, top.barcode)
        null -> Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEachIndexed { i, t ->
                        val label = stringResource(t.label)
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.DIARY -> Icons.Filled.Restaurant
                                        Tab.PROGRESS -> Icons.AutoMirrored.Filled.ShowChart
                                        Tab.PROFILE -> Icons.Filled.Person
                                    },
                                    contentDescription = label,
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { inner ->
            val m = Modifier.padding(inner)
            when (Tab.entries[tab]) {
                Tab.DIARY -> DiaryScreen(vm, onOpenProfile = { tab = 2 }, modifier = m)
                Tab.PROGRESS -> ProgressScreen(vm, m)
                Tab.PROFILE -> ProfileScreen(vm, m)
            }
        }
    }
    if (vm.stack.isNotEmpty()) SnackbarOverlay(snackbar)
    FoodSheetHost(vm)
}

/** Pushed pages have their own Scaffold; keep messages visible over them too (above their bottom bar). */
@Composable
private fun SnackbarOverlay(state: SnackbarHostState) {
    Box(
        Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) { SnackbarHost(state) }
}
