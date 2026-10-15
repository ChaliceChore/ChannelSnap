package dev.chalicechore.channelsnap.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.chalicechore.channelsnap.BuildConfig
import dev.chalicechore.channelsnap.ChannelSnapApp
import dev.chalicechore.channelsnap.ui.components.LogConsole
import dev.chalicechore.channelsnap.ui.screens.home.HomeScreen
import dev.chalicechore.channelsnap.ui.screens.home.HomeViewModel
import dev.chalicechore.channelsnap.ui.screens.restore.RestoreScreen
import dev.chalicechore.channelsnap.ui.screens.restore.RestoreViewModel
import dev.chalicechore.channelsnap.ui.screens.settings.SettingsScreen
import dev.chalicechore.channelsnap.ui.screens.settings.SettingsViewModel
import dev.chalicechore.channelsnap.ui.theme.ChannelSnapTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ChannelSnapApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            ChannelSnapTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                ChannelSnapRoot(app)
            }
        }
    }
}

private enum class Tab(val label: String) { HOME("Home"), SETTINGS("Settings") }

/**
 * Two tabs plus a full-screen Restore flow. With this few destinations, plain state
 * is simpler than a navigation library and keeps restore results in a ViewModel
 * scoped to the chosen file.
 */
@Composable
private fun ChannelSnapRoot(app: ChannelSnapApp) {
    var tab by rememberSaveable { mutableIntStateOf(Tab.HOME.ordinal) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val homeVm: HomeViewModel = viewModel(factory = viewModelFactory {
        initializer { HomeViewModel(app.repository, app.settings) }
    })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app.repository, app.settings) }
    })
    val snackbar = remember { SnackbarHostState() }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    AnimatedContent(
        targetState = restoreUri,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally { it / 3 } + fadeIn() togetherWith fadeOut()
            } else {
                fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
            }
        },
        label = "root",
    ) { uri ->
        if (uri != null) {
            val restoreVm: RestoreViewModel = viewModel(
                key = "restore:$uri",
                factory = viewModelFactory { initializer { RestoreViewModel(app.repository, uri) } },
            )
            BackHandler { restoreUri = null }
            RestoreScreen(restoreVm, onClose = { restoreUri = null })
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        if (BuildConfig.DEBUG) {
                            val log by app.repository.log.collectAsStateWithLifecycle()
                            LogConsole(log, onClear = app.repository::clearLog)
                        }
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                val selected = tab == t.ordinal
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { tab = t.ordinal },
                                    icon = { Icon(t.icon(selected), contentDescription = null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                when (Tab.entries[tab]) {
                    Tab.HOME -> HomeScreen(
                        vm = homeVm,
                        snackbar = snackbar,
                        // JSON pickers often grey out files saved with a generic MIME type.
                        onRestore = { pickBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                        modifier = Modifier.padding(padding),
                    )
                    Tab.SETTINGS -> SettingsScreen(settingsVm, Modifier.padding(padding))
                }
            }
        }
    }
}

private fun Tab.icon(selected: Boolean) = when (this) {
    Tab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    Tab.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
}
