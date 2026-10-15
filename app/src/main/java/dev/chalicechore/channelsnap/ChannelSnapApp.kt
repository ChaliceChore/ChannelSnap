package dev.chalicechore.channelsnap

import android.app.Application
import dev.chalicechore.channelsnap.data.SettingsStore
import dev.chalicechore.channelsnap.repository.BackupRepository

/** Holds the app-wide singletons; small enough that a DI framework isn't worth it. */
class ChannelSnapApp : Application() {
    val repository by lazy { BackupRepository(this) }
    val settings by lazy { SettingsStore(this) }
}
