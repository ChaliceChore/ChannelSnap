package dev.chalicechore.channelsnap.ui.screens.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chalicechore.channelsnap.BuildConfig
import dev.chalicechore.channelsnap.data.ThemeMode
import dev.chalicechore.channelsnap.ui.AppLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val folderName by vm.folderName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openUrl = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            Section("Backups")
            ListItem(
                headlineContent = { Text("Backup folder") },
                supportingContent = {
                    Text(if (settings.backupFolder == null) "Ask where to save each backup" else folderName ?: "…")
                },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                trailingContent = {
                    if (settings.backupFolder != null) {
                        IconButton(onClick = { vm.setBackupFolder(null) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Stop using this folder")
                        }
                    }
                },
                modifier = Modifier.clickable { pickFolder.launch(settings.backupFolder) },
            )

            Section("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                leadingContent = { Icon(Icons.Rounded.Palette, null) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val modes = ThemeMode.entries
                        modes.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Dynamic colour") },
                    supportingContent = { Text("Match your wallpaper colours") },
                    leadingContent = { Icon(Icons.Rounded.Palette, null) },
                    trailingContent = {
                        Switch(checked = settings.dynamicColor, onCheckedChange = vm::setDynamicColor)
                    },
                    modifier = Modifier.clickable { vm.setDynamicColor(!settings.dynamicColor) },
                )
            }

            Section("About")
            ListItem(
                headlineContent = { Text("ChannelSnap ${BuildConfig.VERSION_NAME}") },
                supportingContent = {
                    Text(
                        "Back up and restore blocked notification channels. Root (Magisk or " +
                            "KernelSU) is required.",
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Info, null) },
            )
            LinkItem("Source code", "View the project on GitHub", Icons.Rounded.Code, AppLinks.SOURCE, openUrl)
            LinkItem("Report an issue", "Bugs, device quirks, feature ideas", Icons.Rounded.BugReport, AppLinks.ISSUES, openUrl)
            LinkItem("Support development", "Buy the developer a coffee", Icons.Rounded.VolunteerActivism, AppLinks.DONATE, openUrl)
            LinkItem("License", "MIT", Icons.Rounded.Gavel, AppLinks.LICENSE, openUrl)

            Section("Credits")
            ListItem(
                headlineContent = { Text("Built with") },
                supportingContent = {
                    Text(
                        "Kotlin, Jetpack Compose, Material 3 and kotlinx.serialization. Root access " +
                            "through Magisk or KernelSU. Restore uses the same system API as the " +
                            "Settings app.",
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Favorite, null) },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun LinkItem(title: String, subtitle: String, icon: ImageVector, url: String, open: (String) -> Unit) {
    if (url.isBlank()) return
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
        modifier = Modifier.clickable { open(url) },
    )
}
