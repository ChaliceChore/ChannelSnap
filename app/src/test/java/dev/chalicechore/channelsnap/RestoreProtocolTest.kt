package dev.chalicechore.channelsnap

import dev.chalicechore.channelsnap.root.RestoreProtocol
import dev.chalicechore.channelsnap.root.RestoreProtocol.PlanItem
import dev.chalicechore.channelsnap.root.RestoreProtocol.Report
import dev.chalicechore.channelsnap.root.RestoreProtocol.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreProtocolTest {

    @Test fun planRoundTripsAwkwardIds() {
        val plan = listOf(
            PlanItem(0, "com.foo", 10123, "plain"),
            PlanItem(3, "com.bar", 10456, "it's | a\ttab & \"quote\" \$(rm -rf)"),
        )
        assertEquals(plan, RestoreProtocol.decodePlan(RestoreProtocol.encodePlan(plan)))
    }

    @Test fun reportsSurviveNoiseAndMultilineDetails() {
        val stdout = buildString {
            appendLine(RestoreProtocol.READY)
            appendLine("some warning from app_process")
            appendLine(RestoreProtocol.encodeReport(Report(0, Status.BLOCKED, null)))
            appendLine(RestoreProtocol.encodeReport(Report(3, Status.FAILED, "SecurityException:\nnope\tat x")))
            appendLine("__DONE__garbage")
        }
        val reports = RestoreProtocol.decodeReports(stdout)
        assertEquals(setOf(0, 3), reports.keys)
        assertEquals(Status.BLOCKED, reports.getValue(0).status)
        assertEquals(null, reports.getValue(0).detail)
        assertEquals("SecurityException: nope at x", reports.getValue(3).detail)
    }
}
