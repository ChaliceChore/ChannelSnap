package dev.chalicechore.channelsnap.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chalicechore.channelsnap.data.ChannelSnapException
import dev.chalicechore.channelsnap.data.SettingsStore
import dev.chalicechore.channelsnap.repository.BackupRepository
import dev.chalicechore.channelsnap.ui.components.AppGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: BackupRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        checkRoot()
    }

    fun checkRoot() {
        _state.update { it.copy(rootStatus = RootStatus.CHECKING) }
        viewModelScope.launch {
            val granted = repo.isRootAvailable()
            _state.update { it.copy(rootStatus = if (granted) RootStatus.GRANTED else RootStatus.MISSING) }
        }
    }

    fun scan() {
        if (_state.value.busy) return
        _state.update {
            it.copy(scan = ScanState.Scanning("Checking root access…"), selected = emptySet(), collapsed = emptySet(), query = "")
        }
        viewModelScope.launch {
            val granted = repo.isRootAvailable()
            _state.update { it.copy(rootStatus = if (granted) RootStatus.GRANTED else RootStatus.MISSING) }
            if (!granted) {
                _state.update {
                    it.copy(
                        scan = ScanState.Error(
                            "ChannelSnap needs root to read notification settings. Open Magisk or " +
                                "KernelSU, allow root for ChannelSnap, then try again.",
                            rootMissing = true,
                        ),
                    )
                }
                return@launch
            }
            _state.update { it.copy(scan = ScanState.Scanning("Reading notification settings…")) }
            try {
                val channels = repo.scan()
                _state.update {
                    // Everything starts selected, so a backup is one tap away.
                    it.copy(scan = ScanState.Loaded(channels), selected = channels.mapTo(HashSet()) { c -> c.key })
                }
            } catch (e: ChannelSnapException) {
                _state.update { it.copy(scan = ScanState.Error(e.message.orEmpty(), e.rootMissing)) }
            } catch (e: Exception) {
                _state.update { it.copy(scan = ScanState.Error("Scan failed: ${e.message ?: e.javaClass.simpleName}", false)) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun toggle(key: String) = _state.update {
        it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
    }

    fun toggleGroup(group: AppGroup) = _state.update { s ->
        val keys = group.channels.map { it.key }
        s.copy(selected = if (keys.all { it in s.selected }) s.selected - keys.toSet() else s.selected + keys)
    }

    fun toggleCollapsed(pkg: String) = _state.update {
        it.copy(collapsed = if (pkg in it.collapsed) it.collapsed - pkg else it.collapsed + pkg)
    }

    // ── Batch actions: they apply to what the search currently shows ──

    fun selectAll() = _state.update { s -> s.copy(selected = s.selected + s.visibleChannels.map { it.key }) }

    fun selectNone() = _state.update { s -> s.copy(selected = s.selected - s.visibleChannels.map { it.key }.toSet()) }

    fun invertSelection() = _state.update { s ->
        val visible = s.visibleChannels.map { it.key }.toSet()
        s.copy(selected = (s.selected - visible) + (visible - s.selected))
    }

    fun toggleCollapseAll() = _state.update { s ->
        val pkgs = s.visibleGroups.map { it.packageName }.toSet()
        s.copy(collapsed = if (pkgs.all { it in s.collapsed }) s.collapsed - pkgs else s.collapsed + pkgs)
    }

    // ── Backup ──

    fun backUp() {
        val s = _state.value
        if (s.busy) return
        val entries = s.selectedChannels
        if (entries.isEmpty()) {
            send(HomeEffect.Message("Select at least one channel to back up."))
            return
        }
        val folder = settings.settings.value.backupFolder
        if (folder == null) {
            send(HomeEffect.PickSaveLocation(repo.suggestedFileName()))
            return
        }
        save { repo.writeBackupToFolder(folder, entries) }
    }

    /** Result of the "save as" picker; null when the user cancelled. */
    fun onSaveLocationPicked(uri: Uri?) {
        if (uri == null) return
        val entries = _state.value.selectedChannels
        save { repo.writeBackup(uri, entries); uri }
    }

    private fun save(write: suspend () -> Uri) {
        val count = _state.value.selectedChannels.size
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val uri = write()
                send(HomeEffect.Message("Backed up $count channel${if (count == 1) "" else "s"}", share = uri))
            } catch (e: ChannelSnapException) {
                send(HomeEffect.Message(e.message.orEmpty()))
            } catch (e: Exception) {
                send(HomeEffect.Message("Backup failed: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun send(effect: HomeEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
