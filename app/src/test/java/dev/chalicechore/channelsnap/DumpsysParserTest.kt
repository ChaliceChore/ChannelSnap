package dev.chalicechore.channelsnap

import dev.chalicechore.channelsnap.data.DumpsysParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DumpsysParserTest {

    // Trimmed from a real Android 16 dump (Pixel 8a); channel lines keep their nested braces.
    private val dump = """
        Notification List:
          NotificationRecord(0x0a1b2c3d: pkg=com.whatsapp user=UserHandle{0} id=1 tag=null importance=4 key=0|com.whatsapp|1|null|10233: Notification(channel=msg))
            uid=10233 userId=0
        mRankingHelper / PreferencesHelper:
              AppSettings: com.android.configupdater (1000) importance=NONE userSet=false
              AppSettings: com.rapido.passenger (10350) importance=DEFAULT userSet=true
                Delegate: com.google.android.gms (10161) enabled=true
                NotificationChannel{mId='promotional', mName=Discounts and News, mDescription=, mImportance=0, mBypassDnd=false, mLockscreenVisibility=-1000, mSound=content://settings/system/notification_sound, mLights=false, mLightColor=0, mVibrationPattern=null, mVibrationEffect=null, mUserLockedFields=4, mUserVisibleTaskShown=false, mVibrationEnabled=false, mShowBadge=true, mDeleted=false, mDeletedTimeMs=-1, mGroup='null', mAudioAttributes=AudioAttributes: usage=USAGE_NOTIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null, mBlockableSystem=false, mAllowBubbles=-1, mImportanceLockedDefaultApp=false, mOriginalImp=3, mParent=null, mConversationId=null, mDemoted=false, mImportantConvo=false, mLastNotificationUpdateTimeMs=0}
                NotificationChannel{mId='transactional', mName=Ride and account updates, mDescription=, mImportance=5, mGroup='null', mAudioAttributes=AudioAttributes: usage=USAGE_NOTIFICATION bundle=null}
              AppSettings: dev.chalicechore.channelsnap (10500) importance=DEFAULT userSet=false
                NotificationChannel{mId='self', mName=Self, mImportance=0, mGroup='null'}
              AppSettings: com.example.chat (10400) importance=DEFAULT userSet=true
                NotificationChannel{mId='calls msgs', mName=Calls, messages and more, mDescription=Stuff, mImportance=0, mGroup='g1', mVibrationEffect=VibrationEffect{segments=[]}}
                NotificationChannel{mId='promotional', mName=Discounts and News, mImportance=0}
                NotificationChannel{mId='promotional', mName=Discounts and News, mImportance=0}
                NotificationChannel{mId='noname', mName=null, mImportance=0}
    """.trimIndent()

    private val labels = mapOf("com.rapido.passenger" to "Rapido", "com.example.chat" to "Chat")

    private fun parse() = DumpsysParser.parse(dump, { labels[it] ?: it }, excludePackage = "dev.chalicechore.channelsnap")

    @Test fun findsOnlyBlockedChannels() {
        val keys = parse().map { it.key }
        assertTrue("com.rapido.passenger|promotional" in keys)
        assertTrue("com.rapido.passenger|transactional" !in keys)
    }

    @Test fun excludesSelfWithoutMisattributing() {
        val entries = parse()
        assertTrue(entries.none { it.packageName == "dev.chalicechore.channelsnap" })
        assertTrue(entries.none { it.channelId == "self" })
    }

    @Test fun deduplicatesAndSorts() {
        val entries = parse()
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
        assertEquals(listOf("Chat", "Chat", "Chat", "Rapido"), entries.map { it.appLabel })
        assertEquals(listOf("Calls, messages and more", "Discounts and News", "noname"), entries.take(3).map { it.channelName })
    }

    @Test fun keepsCommasInNamesAndSpacesInIds() {
        val e = parse().first { it.packageName == "com.example.chat" && it.channelId == "calls msgs" }
        assertEquals("Calls, messages and more", e.channelName)
    }

    @Test fun nullNameFallsBackToId() {
        assertEquals("noname", parse().first { it.channelId == "noname" }.channelName)
    }
}
