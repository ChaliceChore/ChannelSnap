package dev.chalicechore.channelsnap.model

/** What happened to one channel during a restore. */
enum class RestoreOutcome {
    /** Channel is now blocked. */
    BLOCKED,

    /** Channel was already blocked; nothing changed. */
    ALREADY_BLOCKED,

    /** The app isn't installed on this device. */
    APP_MISSING,

    /**
     * The app is installed but hasn't created this channel yet. Apps create their
     * channels at runtime, so opening the app once usually fixes this.
     */
    CHANNEL_MISSING,

    /** The system rejected the change, or the root helper didn't report back. */
    FAILED,
}

data class ChannelRestoreResult(
    val entry: ChannelBackupEntry,
    val outcome: RestoreOutcome,
    val detail: String? = null,
)

data class RestoreResult(val channels: List<ChannelRestoreResult>) {
    fun count(outcome: RestoreOutcome) = channels.count { it.outcome == outcome }

    val succeeded: Int get() = count(RestoreOutcome.BLOCKED) + count(RestoreOutcome.ALREADY_BLOCKED)
}
