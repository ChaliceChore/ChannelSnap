package dev.chalicechore.channelsnap.data

import dev.chalicechore.channelsnap.model.ChannelBackupEntry

/**
 * Extracts blocked channels from `dumpsys notification --noredact` output.
 *
 * Pure Kotlin with no Android dependencies, so it is unit-tested against captured
 * dump fragments (see `DumpsysParserTest`).
 *
 * The channel registry looks like this on Android 14–16 (each channel is one long line):
 * ```
 * AppSettings: com.rapido.passenger (10350) importance=DEFAULT userSet=true
 *   Delegate: com.google.android.gms (10161) enabled=true
 *   NotificationChannel{mId='promotional', mName=Discounts and News, mDescription=, mImportance=0, ...}
 * ```
 */
object DumpsysParser {

    /** IMPORTANCE_NONE: the channel is blocked. */
    const val IMPORTANCE_NONE = 0

    // "AppSettings: <pkg> (<uid>)" is the only reliable package anchor in the registry.
    private val appSettingsPattern = Regex("""AppSettings:\s+([\w.]+)\s+\(\d+""")

    // Active notification records carry "pkg=X ... uid=Y" on the same line.
    private val pkgUidPattern = Regex("""\bpkg=([\w.]+)\b""")

    // Lazy, so a block ends at the first '}'. Nested braces (AudioAttributes,
    // VibrationEffect) come after mId/mName/mImportance, so truncating there loses
    // nothing we read.
    private val blockPattern = Regex("""NotificationChannel\{.*?\}""")

    private val idPattern = Regex("""\bmId='([^']*)'""")

    // The name is unquoted. It ends at the next ", mField=" (or the block end) rather
    // than the next comma, so names such as "Calls, messages" survive intact.
    private val namePattern = Regex("""\bmName=(.*?)(?=, m[A-Z]\w*=|\}|$)""")

    private val importancePattern = Regex("""\bmImportance=(\d+)""")

    /**
     * @param labelFor resolves a package name to its app label (falls back to the package).
     * @param excludePackage a package whose channels are never reported (ChannelSnap itself).
     * @param log receives one line per blocked channel found.
     */
    fun parse(
        dump: String,
        labelFor: (String) -> String = { it },
        excludePackage: String? = null,
        log: (String) -> Unit = {},
    ): List<ChannelBackupEntry> {
        val found = ArrayList<ChannelBackupEntry>()
        var currentPkg: String? = null

        for (line in dump.lineSequence()) {
            val trimmed = line.trim()

            if (trimmed.startsWith("AppSettings:")) {
                appSettingsPattern.find(trimmed)?.let { currentPkg = it.groupValues[1] }
            }
            if ("pkg=" in trimmed && "uid=" in trimmed) {
                pkgUidPattern.find(trimmed)?.let { currentPkg = it.groupValues[1] }
            }

            for (match in blockPattern.findAll(line)) {
                val block = match.value

                val importance = importancePattern.find(block)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (importance != IMPORTANCE_NONE) continue

                // The package is always tracked, even when it is excluded; skipping
                // the header instead would pin its channels onto the previous app.
                val pkg = currentPkg ?: continue
                if (pkg == excludePackage) continue

                val id = idPattern.find(block)?.groupValues?.get(1)?.trim() ?: continue
                val name = namePattern.find(block)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "null" } ?: id
                val label = labelFor(pkg)

                found += ChannelBackupEntry(pkg, label, id, name)
                log("  [BLOCKED] $label / $name ($pkg)")
            }
        }

        return found
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.channelName.lowercase() }))
    }
}
