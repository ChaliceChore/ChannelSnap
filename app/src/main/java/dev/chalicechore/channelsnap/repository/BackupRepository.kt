package dev.chalicechore.channelsnap.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import dev.chalicechore.channelsnap.data.ChannelSnapException
import dev.chalicechore.channelsnap.data.NotificationBackupManager
import dev.chalicechore.channelsnap.model.BackupFile
import dev.chalicechore.channelsnap.model.ChannelBackupEntry
import dev.chalicechore.channelsnap.model.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Suspend-friendly facade over [NotificationBackupManager] plus backup file I/O.
 * Files go through the Storage Access Framework, so backups can live anywhere the
 * user can reach (Downloads, a synced folder, a USB drive) with no storage permission.
 */
class BackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val manager = NotificationBackupManager(appContext)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val _log = MutableStateFlow<List<String>>(emptyList())

    /** Engine log for the debug console, shared by every screen. Capped to the last 2000 lines. */
    val log: StateFlow<List<String>> = _log.asStateFlow()

    init {
        manager.logger = { line -> _log.update { (it + line).takeLast(2000) } }
    }

    fun clearLog() = _log.update { emptyList() }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) { manager.isRootAvailable() }

    suspend fun scan(): List<ChannelBackupEntry> = withContext(Dispatchers.IO) { manager.scanBlockedChannels() }

    suspend fun restore(entries: List<ChannelBackupEntry>): RestoreResult =
        withContext(Dispatchers.IO) { manager.restore(entries) }

    /** File name for a new backup, e.g. `channelsnap-Pixel_8a-2026-10-15T09-30-00Z.json`. */
    fun suggestedFileName(): String {
        val ts = FILE_TS.format(Instant.now())
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "_")
        return "channelsnap-$model-$ts.json"
    }

    /** Writes a backup into the [folder] tree under a new file and returns its Uri. */
    suspend fun writeBackupToFolder(folder: Uri, entries: List<ChannelBackupEntry>): Uri =
        withContext(Dispatchers.IO) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                folder, DocumentsContract.getTreeDocumentId(folder),
            )
            val doc = try {
                DocumentsContract.createDocument(resolver, parent, MIME_JSON, suggestedFileName())
            } catch (e: Exception) {
                null
            } ?: throw ChannelSnapException(
                "Couldn't create a file in the backup folder. It may have been moved or " +
                    "access was revoked — choose the folder again in Settings.",
            )
            writeBackup(doc, entries)
            doc
        }

    /** Writes a backup into a document the user already created (CreateDocument picker). */
    suspend fun writeBackup(target: Uri, entries: List<ChannelBackupEntry>) = withContext(Dispatchers.IO) {
        val backup = BackupFile(
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            backupTimestamp = ISO_TS.format(Instant.now()),
            totalDisabledChannels = entries.size,
            channels = entries,
        )
        val out = resolver.openOutputStream(target, "wt")
            ?: throw ChannelSnapException("Couldn't open the backup file for writing.")
        out.bufferedWriter().use { it.write(json.encodeToString(BackupFile.serializer(), backup)) }
    }

    fun isInstalled(pkg: String): Boolean =
        runCatching { appContext.packageManager.getApplicationInfo(pkg, 0) }.isSuccess

    suspend fun readBackup(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw ChannelSnapException("Couldn't open that file.")
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw ChannelSnapException("That file isn't a ChannelSnap backup.")
        } catch (e: IllegalArgumentException) {
            throw ChannelSnapException("That file isn't a ChannelSnap backup.")
        }
        if (backup.channels.isEmpty()) throw ChannelSnapException("That backup contains no channels.")
        backup
    }

    /** Keeps access to a picked backup folder across reboots, releasing the previous one. */
    fun persistFolderAccess(folder: Uri?, previous: Uri?) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (previous != null && previous != folder) {
            runCatching { resolver.releasePersistableUriPermission(previous, flags) }
        }
        if (folder != null) resolver.takePersistableUriPermission(folder, flags)
    }

    /** Human-readable name of a picked folder, for Settings. */
    suspend fun folderDisplayName(folder: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(
                folder, DocumentsContract.getTreeDocumentId(folder),
            )
            resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    companion object {
        const val MIME_JSON = "application/json"
        private val ISO_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
        private val FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC)
    }
}
