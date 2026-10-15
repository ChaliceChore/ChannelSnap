package dev.chalicechore.channelsnap.data

import android.util.Log
import kotlin.concurrent.thread

/**
 * Thin wrapper around `su`. Works on Magisk and KernelSU with no extra dependency.
 *
 * Commands are written to the stdin of a single `su` process, followed by `exit`,
 * and stdout is read only once the process has finished — the output of
 * `dumpsys notification` (~750 KB) isn't reliably complete before that.
 *
 * Every function blocks; call them from [kotlinx.coroutines.Dispatchers.IO].
 */
object RootShell {

    private const val TAG = "RootShell"

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val success: Boolean get() = exitCode == 0
    }

    /** True if `su` exists and has already granted this app root. */
    fun isRootAvailable(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val line = p.inputStream.bufferedReader().use { it.readLine() }
        p.waitFor() == 0 && line != null && line.contains("uid=0")
    } catch (e: Exception) {
        Log.w(TAG, "Root check failed: ${e.message}")
        false
    }

    /** Runs [commands] in one `su` session, i.e. behind a single root grant. */
    fun runCommands(commands: List<String>): Result {
        val err = StringBuilder()
        return try {
            val su = Runtime.getRuntime().exec("su")

            // Drain stderr on its own thread: if a command writes enough to fill the
            // stderr pipe while we're blocked reading stdout, both sides deadlock.
            val errReader = thread(name = "su-stderr") {
                su.errorStream.bufferedReader().forEachLine { err.append(it).append('\n') }
            }

            su.outputStream.bufferedWriter().use { w ->
                commands.forEach { w.write(it); w.write("\n") }
                w.write("exit\n")
            }

            val out = su.inputStream.bufferedReader().readText()
            val exit = su.waitFor()
            errReader.join()
            Result(exit, out, err.toString())
        } catch (e: Exception) {
            Log.e(TAG, "su execution error", e)
            Result(-1, "", err.append(e.message).toString())
        }
    }

    fun run(command: String): Result = runCommands(listOf(command))
}
