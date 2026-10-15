package dev.chalicechore.channelsnap.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chalicechore.channelsnap.data.Settings
import dev.chalicechore.channelsnap.data.SettingsStore
import dev.chalicechore.channelsnap.data.ThemeMode
import dev.chalicechore.channelsnap.repository.BackupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

class SettingsViewModel(
    private val repo: BackupRepository,
    private val store: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings

    /** Display name of the backup folder; null when unset or no longer reachable. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val folderName: StateFlow<String?> = store.settings
        .mapLatest { s -> s.backupFolder?.let { repo.folderDisplayName(it) ?: "Unavailable folder" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBackupFolder(uri: Uri?) {
        runCatching { repo.persistFolderAccess(uri, store.settings.value.backupFolder) }
        store.setBackupFolder(uri)
    }

    fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = store.setDynamicColor(enabled)
}
