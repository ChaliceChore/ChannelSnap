package dev.chalicechore.channelsnap.ui.screens.home

import android.net.Uri
import dev.chalicechore.channelsnap.model.ChannelBackupEntry
import dev.chalicechore.channelsnap.ui.components.AppGroup
import dev.chalicechore.channelsnap.ui.components.groupByApp

enum class RootStatus { CHECKING, GRANTED, MISSING }

sealed interface ScanState {
    /** Nothing scanned yet this session. */
    data object Idle : ScanState
    data class Scanning(val step: String) : ScanState
    data class Loaded(val channels: List<ChannelBackupEntry>) : ScanState
    data class Error(val message: String, val rootMissing: Boolean) : ScanState
}

data class HomeUiState(
    val rootStatus: RootStatus = RootStatus.CHECKING,
    val scan: ScanState = ScanState.Idle,
    val selected: Set<String> = emptySet(),
    val collapsed: Set<String> = emptySet(),
    val query: String = "",
    val saving: Boolean = false,
) {
    val channels: List<ChannelBackupEntry> get() = (scan as? ScanState.Loaded)?.channels.orEmpty()

    /** Channels matching the search box; batch actions apply to these. */
    val visibleChannels: List<ChannelBackupEntry>
        get() = if (query.isBlank()) channels else channels.filter { it.matches(query.trim()) }

    val visibleGroups: List<AppGroup> get() = visibleChannels.groupByApp()

    val selectedChannels: List<ChannelBackupEntry> get() = channels.filter { it.key in selected }

    val busy: Boolean get() = saving || scan is ScanState.Scanning
}

private fun ChannelBackupEntry.matches(q: String) =
    appLabel.contains(q, true) || packageName.contains(q, true) ||
        channelName.contains(q, true) || channelId.contains(q, true)

sealed interface HomeEffect {
    data class Message(val text: String, val share: Uri? = null) : HomeEffect

    /** No backup folder configured: ask the user where to save this one. */
    data class PickSaveLocation(val suggestedName: String) : HomeEffect
}
