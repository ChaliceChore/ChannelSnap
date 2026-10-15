package dev.chalicechore.channelsnap.root

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.IBinder
import dev.chalicechore.channelsnap.root.RestoreProtocol.Report
import dev.chalicechore.channelsnap.root.RestoreProtocol.Status
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Root-side restore helper, launched by [RootRestoreHelper] as
 * `CLASSPATH=<apk> app_process /system/bin dev.chalicechore.channelsnap.root.RestoreMain <plan>`
 * inside the same `su` session, so it runs as uid 0 rather than in the app.
 *
 * Why not `cmd notification`: `NotificationShellCmd` has no command that sets a
 * channel's importance (only `set_bubbles_channel`). The binder methods Settings
 * uses, however, admit root explicitly in `NotificationManagerService`:
 *  - `getNotificationChannelsForPackage` → `enforceSystemOrSystemUI` → `isUidSystemOrPhone`, which includes ROOT_UID
 *  - `updateNotificationChannelForPackage` → `checkCallerIsSystemOrSystemUiOrShell`, which includes ROOT_UID
 *
 * The update goes down the same path as the user toggling the channel in Settings,
 * so importance is user-locked and the app can't raise it again. Both signatures
 * have been unchanged since API 26.
 */
object RestoreMain {

    @JvmStatic
    fun main(args: Array<String>) {
        println(RestoreProtocol.READY)
        val plan = RestoreProtocol.decodePlan(File(args[0]).readText())

        val nm = try {
            NotificationService()
        } catch (t: Throwable) {
            val msg = "Cannot reach the notification service: ${t.rootMessage()}"
            plan.forEach { println(RestoreProtocol.encodeReport(Report(it.index, Status.FAILED, msg))) }
            return
        }

        for (item in plan) {
            val report = try {
                restoreOne(nm, item)
            } catch (t: Throwable) {
                Report(item.index, Status.FAILED, t.rootMessage())
            }
            println(RestoreProtocol.encodeReport(report))
        }
        System.out.flush()
    }

    private fun restoreOne(nm: NotificationService, item: RestoreProtocol.PlanItem): Report {
        val channel = nm.channels(item.packageName, item.uid).firstOrNull { it.id == item.channelId }
            ?: return Report(item.index, Status.CHANNEL_MISSING, null)

        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return Report(item.index, Status.ALREADY_BLOCKED, null)

        channel.importance = NotificationManager.IMPORTANCE_NONE
        nm.update(item.packageName, item.uid, channel)

        // Read the channel back: the system can silently ignore the change, e.g. for
        // channels it considers non-blockable.
        val after = nm.channels(item.packageName, item.uid).firstOrNull { it.id == item.channelId }
        return if (after?.importance == NotificationManager.IMPORTANCE_NONE) {
            Report(item.index, Status.BLOCKED, null)
        } else {
            Report(item.index, Status.FAILED, "System kept importance ${after?.importance}; channel may be non-blockable")
        }
    }

    private fun Throwable.rootMessage(): String {
        var t: Throwable = this
        while (t is InvocationTargetException && t.targetException != null) t = t.targetException
        return "${t.javaClass.simpleName}: ${t.message}"
    }

    /** Reflection over the hidden `INotificationManager` binder interface (fine here: uid 0, no app hidden-API policy). */
    @SuppressLint("PrivateApi")
    private class NotificationService {
        private val service: Any
        private val getChannels: Method
        private val updateChannel: Method

        init {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "notification") as IBinder
            val iface = Class.forName("android.app.INotificationManager")
            service = Class.forName("android.app.INotificationManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)!!
            // Resolved on the interface, not the private Stub.Proxy, so invoke() is allowed.
            getChannels = iface.getMethod(
                "getNotificationChannelsForPackage",
                String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            )
            updateChannel = iface.getMethod(
                "updateNotificationChannelForPackage",
                String::class.java, Int::class.javaPrimitiveType, NotificationChannel::class.java,
            )
        }

        fun channels(pkg: String, uid: Int): List<NotificationChannel> {
            val slice = getChannels.invoke(service, pkg, uid, false) ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            return slice.javaClass.getMethod("getList").invoke(slice) as List<NotificationChannel>
        }

        fun update(pkg: String, uid: Int, channel: NotificationChannel) {
            updateChannel.invoke(service, pkg, uid, channel)
        }
    }
}
