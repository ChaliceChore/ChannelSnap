package dev.chalicechore.channelsnap.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** SAF tree the user picked for backups; null means "ask every time". */
    val backupFolder: Uri? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

/** A handful of preferences, so plain SharedPreferences is enough (no DataStore). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun load() = Settings(
        backupFolder = prefs.getString(KEY_FOLDER, null)?.toUri(),
        themeMode = prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
    )

    fun setBackupFolder(uri: Uri?) {
        prefs.edit { putString(KEY_FOLDER, uri?.toString()) }
        _settings.update { it.copy(backupFolder = uri) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _settings.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DYNAMIC, enabled) }
        _settings.update { it.copy(dynamicColor = enabled) }
    }

    private companion object {
        const val KEY_FOLDER = "backup_folder"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}
