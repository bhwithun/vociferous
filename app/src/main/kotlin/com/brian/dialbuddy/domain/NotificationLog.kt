package com.brian.dialbuddy.domain

const val NOTICE_LIMIT = 400
const val OWN_PACKAGE = "com.brian.dialbuddy"

private const val FIELD = '\u001f'

data class Notice(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val updatedAt: Long,
    val dismissed: Boolean,
)

fun recordPosted(current: List<Notice>, incoming: Notice, ownPackage: String = OWN_PACKAGE): List<Notice> {
    if (incoming.packageName == ownPackage || incoming.key.isBlank()) return current
    val previous = current.find { it.key == incoming.key }
    val next = if (previous == null) {
        incoming.copy(dismissed = false)
    } else {
        incoming.copy(postedAt = previous.postedAt, dismissed = false)
    }
    return capNotices(current.filter { it.key != incoming.key } + next)
}

fun recordDismissed(current: List<Notice>, key: String, at: Long): List<Notice> {
    return current.map { notice ->
        if (notice.key == key && !notice.dismissed) {
            notice.copy(dismissed = true, updatedAt = maxOf(notice.updatedAt, at))
        } else {
            notice
        }
    }
}

fun syncNotices(
    current: List<Notice>,
    live: List<Notice>,
    now: Long,
    ownPackage: String = OWN_PACKAGE,
): List<Notice> {
    val liveKeys = live.map { it.key }.toSet()
    var next = current
    for (existing in current) {
        if (!existing.dismissed && existing.key !in liveKeys) {
            next = recordDismissed(next, existing.key, now)
        }
    }
    for (notice in live) {
        next = recordPosted(next, notice, ownPackage)
    }
    return next
}

data class NoticeApp(
    val packageName: String,
    val appName: String,
    val count: Int,
)

fun noticeApps(notices: List<Notice>): List<NoticeApp> {
    return notices
        .groupBy { it.packageName }
        .map { (packageName, rows) ->
            val newest = rows.maxBy { it.updatedAt }
            NoticeApp(packageName, newest.appName.ifBlank { packageName }, rows.size) to newest.updatedAt
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

fun removeNotices(current: List<Notice>, keys: Set<String>): List<Notice> {
    if (keys.isEmpty()) return current
    return current.filter { it.key !in keys }
}

fun capNotices(current: List<Notice>, limit: Int = NOTICE_LIMIT): List<Notice> {
    return current.sortedByDescending { it.updatedAt }.take(limit)
}

fun encodeNotices(notices: List<Notice>): String {
    return notices.joinToString("\n") { notice ->
        listOf(
            notice.key,
            notice.packageName,
            notice.appName,
            notice.title,
            notice.text,
            notice.postedAt.toString(),
            notice.updatedAt.toString(),
            if (notice.dismissed) "1" else "0",
        ).joinToString(FIELD.toString()) { escapeField(it) }
    }
}

fun decodeNotices(raw: String): List<Notice> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        if (line.isEmpty()) return@mapNotNull null
        val parts = splitFields(line)
        if (parts.size != 8) return@mapNotNull null
        Notice(
            key = parts[0],
            packageName = parts[1],
            appName = parts[2],
            title = parts[3],
            text = parts[4],
            postedAt = parts[5].toLongOrNull() ?: return@mapNotNull null,
            updatedAt = parts[6].toLongOrNull() ?: return@mapNotNull null,
            dismissed = parts[7] == "1",
        )
    }.toList()
}

private fun escapeField(value: String): String = buildString {
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            FIELD -> append("\\s")
            else -> append(ch)
        }
    }
}

private fun splitFields(line: String): List<String> {
    val fields = ArrayList<String>(8)
    val current = StringBuilder()
    var index = 0
    while (index < line.length) {
        val ch = line[index]
        if (ch == '\\' && index + 1 < line.length) {
            when (line[index + 1]) {
                '\\' -> current.append('\\')
                'n' -> current.append('\n')
                'r' -> current.append('\r')
                's' -> current.append(FIELD)
                else -> current.append(line[index + 1])
            }
            index += 2
            continue
        }
        if (ch == FIELD) {
            fields.add(current.toString())
            current.clear()
        } else {
            current.append(ch)
        }
        index++
    }
    fields.add(current.toString())
    return fields
}
