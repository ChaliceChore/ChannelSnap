package dev.chalicechore.channelsnap.root

import java.util.Base64

/**
 * Wire format between the app and [RestoreMain], which runs in a separate root
 * process. Pure Kotlin so it is shared by both sides and unit-testable.
 *
 * Plan file (app → helper), one channel per line, strings Base64-encoded so channel
 * ids containing spaces, quotes or tabs survive:
 * ```
 * <index>\t<b64 package>\t<uid>\t<b64 channelId>
 * ```
 * Result lines (helper → app on stdout), one `__DONE__` sentinel per channel:
 * ```
 * __DONE__<index>\t<Status>\t<detail>
 * ```
 * A channel with no sentinel line counts as failed (e.g. the helper crashed).
 */
object RestoreProtocol {

    const val READY = "__CHANNELSNAP_HELPER_READY__"
    private const val DONE = "__DONE__"

    enum class Status { BLOCKED, ALREADY_BLOCKED, CHANNEL_MISSING, FAILED }

    data class PlanItem(val index: Int, val packageName: String, val uid: Int, val channelId: String)

    data class Report(val index: Int, val status: Status, val detail: String?)

    private fun enc(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private fun dec(s: String) = String(Base64.getDecoder().decode(s))

    fun encodePlan(items: List<PlanItem>): String =
        items.joinToString("\n") { "${it.index}\t${enc(it.packageName)}\t${it.uid}\t${enc(it.channelId)}" }

    fun decodePlan(text: String): List<PlanItem> =
        text.lineSequence().filter { it.isNotBlank() }.map { line ->
            val (idx, pkg, uid, id) = line.split('\t')
            PlanItem(idx.toInt(), dec(pkg), uid.toInt(), dec(id))
        }.toList()

    fun encodeReport(r: Report): String {
        val detail = r.detail?.replace(Regex("[\\t\\r\\n]+"), " ").orEmpty()
        return "$DONE${r.index}\t${r.status.name}\t$detail"
    }

    /** Parses every sentinel line in [stdout], ignoring any other output. */
    fun decodeReports(stdout: String): Map<Int, Report> =
        stdout.lineSequence()
            .filter { it.startsWith(DONE) }
            .mapNotNull { line ->
                val parts = line.removePrefix(DONE).split('\t', limit = 3)
                val index = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val status = parts.getOrNull(1)
                    ?.let { s -> Status.entries.firstOrNull { it.name == s } }
                    ?: return@mapNotNull null
                Report(index, status, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
            .associateBy { it.index }
}
