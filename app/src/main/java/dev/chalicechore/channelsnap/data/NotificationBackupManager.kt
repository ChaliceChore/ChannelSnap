package dev.chalicechore.channelsnap.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dev.chalicechore.channelsnap.model.ChannelBackupEntry
import dev.chalicechore.channelsnap.model.ChannelRestoreResult
import dev.chalicechore.channelsnap.model.RestoreOutcome
import dev.chalicechore.channelsnap.model.RestoreResult
import dev.chalicechore.channelsnap.root.RestoreProtocol
import dev.chalicechore.channelsnap.root.RootRestoreHelper

/** Raised when the work can't start at all; the message is shown to the user. */
class ChannelSnapException(message: String, val rootMissing: Boolean = false) : Exception(message)

/**
 * Root-only scan and restore engine. Every function blocks; the repository runs
 * them on the IO dispatcher.
 *
 * - **Scan** runs `dumpsys notification --noredact` as root. The dump is produced
 *   inside system_server, which sidesteps the same-app guard that blocks
 *   `INotificationManager.getNotificationChannels()` for other callers.
 * - **Restore** blocks channels through a root `app_process` helper; see
 *   [dev.chalicechore.channelsnap.root.RestoreMain] for why `cmd notification`
 *   can't do it.
 */
class NotificationBackupManager(context: Context) {

    private val context = context.applicationContext
    private val pm: PackageManager = this.context.packageManager

    /** Line sink for the debug log console. */
    var logger: (String) -> Unit = {}

    private fun log(line: String) {
        Log.d(TAG, line)
        logger(line)
    }

    fun isRootAvailable(): Boolean = RootShell.isRootAvailable()

    fun scanBlockedChannels(): List<ChannelBackupEntry> {
        log("── Scan started ──")
        log("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MODEL}")

        if (!RootShell.isRootAvailable()) {
            log("ERROR: su unavailable or not granted")
            throw ChannelSnapException(ROOT_MISSING, rootMissing = true)
        }

        val labels = buildLabelMap()
        log("Indexed ${labels.size} installed packages")

        log("$ dumpsys notification --noredact")
        val result = RootShell.run("dumpsys notification --noredact")
        log("  ${result.stdout.length} chars, exit=${result.exitCode}")
        if (result.stdout.isBlank()) {
            if (result.stderr.isNotBlank()) log("  stderr: ${result.stderr.trim()}")
            throw ChannelSnapException(
                "The system returned no notification data (exit ${result.exitCode}). " +
                    "Make sure root is granted to ChannelSnap, then try again.",
            )
        }

        val entries = DumpsysParser.parse(
            dump = result.stdout,
            labelFor = { labels[it] ?: it },
            excludePackage = context.packageName,
            log = ::log,
        )
        log("── Scan complete: ${entries.size} blocked channel(s) ──")
        return entries
    }

    fun restore(entries: List<ChannelBackupEntry>): RestoreResult {
        log("── Restore started: ${entries.size} channel(s) ──")
        if (!RootShell.isRootAvailable()) throw ChannelSnapException(ROOT_MISSING, rootMissing = true)

        val results = arrayOfNulls<ChannelRestoreResult>(entries.size)
        val plan = ArrayList<RestoreProtocol.PlanItem>()
        entries.forEachIndexed { i, entry ->
            val uid = uidOf(entry.packageName)
            if (uid == null) {
                results[i] = ChannelRestoreResult(entry, RestoreOutcome.APP_MISSING)
                log("SKIP (not installed): ${entry.appLabel} / ${entry.channelName}")
            } else {
                plan += RestoreProtocol.PlanItem(i, entry.packageName, uid, entry.channelId)
            }
        }

        if (plan.isNotEmpty()) {
            val helper = RootRestoreHelper(context).run(plan)
            log("Helper exit=${helper.shell.exitCode}, started=${helper.started}")
            if (helper.shell.stderr.isNotBlank()) log("stderr:\n${helper.shell.stderr.trim()}")

            val missingReason = if (helper.started) "Root helper reported nothing for this channel"
            else "Root helper failed to start: ${helper.shell.stderr.lineSequence().firstOrNull { it.isNotBlank() } ?: "unknown error"}"

            for (item in plan) {
                val entry = entries[item.index]
                val report = helper.reports[item.index]
                val outcome = when (report?.status) {
                    RestoreProtocol.Status.BLOCKED -> RestoreOutcome.BLOCKED
                    RestoreProtocol.Status.ALREADY_BLOCKED -> RestoreOutcome.ALREADY_BLOCKED
                    RestoreProtocol.Status.CHANNEL_MISSING -> RestoreOutcome.CHANNEL_MISSING
                    RestoreProtocol.Status.FAILED, null -> RestoreOutcome.FAILED
                }
                val detail = report?.detail ?: if (report == null) missingReason else null
                results[item.index] = ChannelRestoreResult(entry, outcome, detail)
                log("$outcome: ${entry.appLabel} / ${entry.channelName}${detail?.let { " — $it" } ?: ""}")
            }
        }

        return RestoreResult(results.map { it!! }).also {
            log("── Restore complete: ${it.succeeded}/${entries.size} blocked ──")
        }
    }

    private fun buildLabelMap(): Map<String, String> =
        pm.getInstalledApplications(0).associate { app ->
            app.packageName to pm.getApplicationLabel(app).toString()
        }

    private fun uidOf(pkg: String): Int? = try {
        pm.getApplicationInfo(pkg, 0).uid
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        private const val TAG = "ChannelSnap"
        private const val ROOT_MISSING =
            "Root access isn't available. Install Magisk or KernelSU and allow root for ChannelSnap."
    }
}
