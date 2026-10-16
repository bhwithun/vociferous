package com.brian.dialbuddy.data

import android.app.Notification
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import com.brian.dialbuddy.domain.Notice
import com.brian.dialbuddy.domain.decodeNotices
import com.brian.dialbuddy.domain.encodeNotices
import com.brian.dialbuddy.domain.recordDismissed
import com.brian.dialbuddy.domain.recordPosted
import com.brian.dialbuddy.domain.removeNotices
import com.brian.dialbuddy.domain.syncNotices
import com.brian.dialbuddy.telecom.NoticeListener
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoticeLog {
    private val lock = Any()
    private val active = HashMap<String, StatusBarNotification>()
    private val _records = MutableStateFlow<List<Notice>>(emptyList())
    val records: StateFlow<List<Notice>> = _records.asStateFlow()
    private var file: File? = null

    fun attach(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val target = File(context.applicationContext.filesDir, "notice-log.txt")
            file = target
            val raw = try {
                if (target.exists()) target.readText() else ""
            } catch (_: IOException) {
                ""
            }
            _records.value = decodeNotices(raw)
        }
    }

    fun posted(context: Context, sbn: StatusBarNotification) {
        attach(context)
        val notice = describe(context, sbn) ?: return
        synchronized(lock) {
            active[notice.key] = sbn
            write(recordPosted(_records.value, notice, context.packageName))
        }
    }

    fun dismissed(key: String, at: Long) {
        synchronized(lock) {
            active.remove(key)
            if (file == null) return
            write(recordDismissed(_records.value, key, at))
        }
    }

    fun sync(context: Context, posted: List<StatusBarNotification>) {
        attach(context)
        val live = ArrayList<Notice>()
        val current = HashMap<String, StatusBarNotification>()
        for (sbn in posted) {
            val notice = describe(context, sbn) ?: continue
            live.add(notice)
            current[notice.key] = sbn
        }
        synchronized(lock) {
            active.clear()
            active.putAll(current)
            write(syncNotices(_records.value, live, System.currentTimeMillis(), context.packageName))
        }
    }

    fun remove(keys: Set<String>) {
        if (keys.isEmpty()) return
        val live = synchronized(lock) {
            val posted = keys.filter { active.containsKey(it) }
            for (key in keys) active.remove(key)
            if (file != null) write(removeNotices(_records.value, keys))
            posted
        }
        for (key in live) NoticeListener.cancel(key)
    }

    fun open(context: Context, key: String): Boolean {
        val pending = synchronized(lock) { active[key]?.notification?.contentIntent }
        val packageName = synchronized(lock) {
            active[key]?.packageName ?: _records.value.firstOrNull { it.key == key }?.packageName
        } ?: return false
        if (pending != null && send(pending)) return true
        return launchApp(context, packageName)
    }

    private fun send(pending: PendingIntent): Boolean {
        return try {
            pending.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun describe(context: Context, sbn: StatusBarNotification): Notice? {
        if (sbn.packageName == context.packageName || sbn.key.isNullOrBlank()) return null
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty().take(200)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val body = (if (text.isNotEmpty()) text else big).take(500)
        return Notice(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appLabel(context, sbn.packageName).take(80),
            title = title,
            text = body,
            postedAt = sbn.postTime,
            updatedAt = System.currentTimeMillis(),
            dismissed = false,
        )
    }

    private fun appLabel(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun write(next: List<Notice>) {
        if (next == _records.value) return
        val target = file ?: return
        try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            val encoded = encodeNotices(next)
            tmp.writeText(encoded)
            if (!tmp.renameTo(target)) {
                target.writeText(encoded)
                tmp.delete()
            }
        } catch (_: IOException) {
        }
        _records.value = next
    }
}
