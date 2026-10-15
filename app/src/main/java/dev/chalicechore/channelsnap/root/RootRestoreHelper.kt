package dev.chalicechore.channelsnap.root

import android.content.Context
import dev.chalicechore.channelsnap.data.RootShell
import java.io.File

/**
 * App-side launcher for [RestoreMain]. Writes the plan to the app's cache, then
 * starts the helper under `app_process` in a single `su` session — one root
 * grant for the whole batch.
 */
class RootRestoreHelper(private val context: Context) {

    class HelperResult(
        val reports: Map<Int, RestoreProtocol.Report>,
        val started: Boolean,
        val shell: RootShell.Result,
    )

    fun run(plan: List<RestoreProtocol.PlanItem>): HelperResult {
        val planFile = File(context.cacheDir, "restore_plan.tsv")
        planFile.writeText(RestoreProtocol.encodePlan(plan))
        try {
            val apk = context.applicationInfo.sourceDir
            val shell = RootShell.runCommands(
                listOf(
                    "export CLASSPATH=${shellQuote(apk)}",
                    "app_process /system/bin --nice-name=channelsnap_restore " +
                        "${RestoreMain::class.java.name} ${shellQuote(planFile.absolutePath)}",
                ),
            )
            return HelperResult(
                reports = RestoreProtocol.decodeReports(shell.stdout),
                started = RestoreProtocol.READY in shell.stdout,
                shell = shell,
            )
        } finally {
            planFile.delete()
        }
    }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
