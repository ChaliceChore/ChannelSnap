package dev.chalicechore.channelsnap.ui.screens.restore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chalicechore.channelsnap.data.ChannelSnapException
import dev.chalicechore.channelsnap.model.BackupFile
import dev.chalicechore.channelsnap.model.RestoreResult
import dev.chalicechore.channelsnap.repository.BackupRepository
import dev.chalicechore.channelsnap.ui.components.AppGroup
import dev.chalicechore.channelsnap.ui.components.groupByApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RestorePhase {
    data object Loading : RestorePhase
    data class Ready(val backup: BackupFile) : RestorePhase
    data class Restoring(val count: Int) : RestorePhase
    data class Done(val result: RestoreResult) : RestorePhase
    data class Error(val message: String, val rootMissing: Boolean = false) : RestorePhase
}

data class RestoreUiState(
    val phase: RestorePhase = RestorePhase.Loading,
    val backup: BackupFile? = null,
    val groups: List<AppGroup> = emptyList(),
    val notInstalled: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val collapsed: Set<String> = emptySet(),
)

class RestoreViewModel(
    private val repo: BackupRepository,
    private val uri: Uri,
) : ViewModel() {

    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        try {
            val backup = repo.readBackup(uri)
            val channels = backup.channels.distinctBy { it.key }
            val missing = withContext(Dispatchers.IO) {
                channels.map { it.packageName }.distinct().filterNot(repo::isInstalled).toSet()
            }
            _state.value = RestoreUiState(
                phase = RestorePhase.Ready(backup),
                backup = backup,
                groups = channels.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.channelName.lowercase() })).groupByApp(),
                notInstalled = missing,
                // Pre-select what can actually be restored; uninstalled apps would only be skipped.
                selected = channels.filter { it.packageName !in missing }.mapTo(HashSet()) { it.key },
                collapsed = missing,
            )
        } catch (e: ChannelSnapException) {
            _state.update { it.copy(phase = RestorePhase.Error(e.message.orEmpty())) }
        } catch (e: Exception) {
            _state.update { it.copy(phase = RestorePhase.Error("Couldn't read the backup: ${e.message}")) }
        }
    }

    private val allChannels get() = _state.value.groups.flatMap { it.channels }

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

    fun selectAll() = _state.update { s -> s.copy(selected = allChannels.mapTo(HashSet()) { it.key }) }

    fun selectNone() = _state.update { it.copy(selected = emptySet()) }

    fun invertSelection() = _state.update { s ->
        s.copy(selected = allChannels.map { it.key }.filterNot { it in s.selected }.toSet())
    }

    fun toggleCollapseAll() = _state.update { s ->
        val pkgs = s.groups.map { it.packageName }.toSet()
        s.copy(collapsed = if (pkgs.all { it in s.collapsed }) emptySet() else pkgs)
    }

    fun restore() {
        val s = _state.value
        if (s.phase !is RestorePhase.Ready) return
        val entries = allChannels.filter { it.key in s.selected }
        if (entries.isEmpty()) return
        _state.update { it.copy(phase = RestorePhase.Restoring(entries.size)) }
        viewModelScope.launch {
            val phase = try {
                RestorePhase.Done(repo.restore(entries))
            } catch (e: ChannelSnapException) {
                RestorePhase.Error(e.message.orEmpty(), e.rootMissing)
            } catch (e: Exception) {
                RestorePhase.Error("Restore failed: ${e.message ?: e.javaClass.simpleName}")
            }
            _state.update { it.copy(phase = phase) }
        }
    }

    /** Back to the selection list, e.g. after opening apps that hadn't created their channels yet. */
    fun backToSelection() {
        val backup = _state.value.backup ?: return
        _state.update { it.copy(phase = RestorePhase.Ready(backup)) }
    }
}
