package dev.chalicechore.channelsnap.model

import kotlinx.serialization.Serializable

/** Root object of a backup JSON file. The format is shared with v1 of the app. */
@Serializable
data class BackupFile(
    val deviceModel: String = "",
    val androidVersion: String = "",
    val backupTimestamp: String = "",
    val totalDisabledChannels: Int = 0,
    val channels: List<ChannelBackupEntry> = emptyList(),
)
