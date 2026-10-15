package dev.chalicechore.channelsnap.model

import kotlinx.serialization.Serializable

/**
 * One blocked notification channel.
 *
 * Only [packageName] and [channelId] are needed to restore it; [appLabel] and
 * [channelName] keep the backup file (and the UI) human-readable.
 */
@Serializable
data class ChannelBackupEntry(
    val packageName: String,
    val appLabel: String,
    val channelId: String,
    val channelName: String,
) {
    /** Stable identity used for de-duplication and selection. */
    val key: String get() = "$packageName|$channelId"
}
