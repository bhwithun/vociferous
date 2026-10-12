package com.brian.dialbuddy.domain

data class VoicemailEntry(
    val id: Long,
    val number: String?,
    val durationSeconds: Int?,
    val dateMillis: Long,
    val archived: Boolean,
    val deleted: Boolean = false,
)

data class DeletionStamp(
    val atMillis: Long,
    val count: Int,
)

const val DEFAULT_THRESHOLD_SECONDS = 3
const val MIN_THRESHOLD_SECONDS = 1
const val MAX_THRESHOLD_SECONDS = 20
const val MILLIS_PER_DAY = 86_400_000L
const val WINDOW_WEEK_MILLIS = 604_800_000L
const val WINDOW_MONTH_MILLIS = 2_592_000_000L
const val WINDOW_YEAR_MILLIS = 31_536_000_000L

fun clampThreshold(seconds: Int): Int =
    seconds.coerceIn(MIN_THRESHOLD_SECONDS, MAX_THRESHOLD_SECONDS)

fun VoicemailEntry.isEmpty(thresholdSeconds: Int): Boolean {
    if (archived || deleted) return false
    val duration = durationSeconds ?: return false
    if (duration < 0) return false
    return duration < thresholdSeconds
}

fun presentVoicemails(entries: List<VoicemailEntry>): List<VoicemailEntry> =
    entries.filterNot { it.deleted }

fun emptyVoicemails(entries: List<VoicemailEntry>, thresholdSeconds: Int): List<VoicemailEntry> {
    val threshold = clampThreshold(thresholdSeconds)
    return entries.filter { it.isEmpty(threshold) }
}

fun VoicemailEntry.qualifiesForBulkDelete(thresholdSeconds: Int, recognized: Boolean): Boolean {
    if (recognized) return false
    return isEmpty(clampThreshold(thresholdSeconds))
}

fun bulkVoicemails(
    entries: List<VoicemailEntry>,
    thresholdSeconds: Int,
    recognizedNumbers: Set<String>,
): List<VoicemailEntry> {
    val threshold = clampThreshold(thresholdSeconds)
    return entries.filter { entry ->
        val number = entry.number?.trim().orEmpty()
        val recognized = number.isNotEmpty() && number in recognizedNumbers
        entry.qualifiesForBulkDelete(threshold, recognized)
    }
}

fun nextDeletedTotal(previousTotal: Long, deletedNow: Int): Long {
    require(previousTotal >= 0) { "total cannot be negative" }
    require(deletedNow >= 0) { "deletedNow cannot be negative" }
    return deletedNow + previousTotal
}

fun deletedInWindow(stamps: List<DeletionStamp>, nowMillis: Long, windowMillis: Long): Long {
    require(windowMillis >= 0) { "window cannot be negative" }
    val start = nowMillis - windowMillis
    var sum = 0L
    for (stamp in stamps) {
        if (stamp.count > 0 && stamp.atMillis in start..nowMillis) {
            sum += stamp.count
        }
    }
    return sum
}

fun retainRecentStamps(stamps: List<DeletionStamp>, nowMillis: Long): List<DeletionStamp> {
    val start = nowMillis - WINDOW_YEAR_MILLIS
    return stamps.filter { it.count > 0 && it.atMillis >= start }
}

fun parseDeletionStamps(stored: String): List<DeletionStamp> {
    if (stored.isBlank()) return emptyList()
    return stored.split(';').mapNotNull { piece ->
        val parts = piece.split(':')
        if (parts.size != 2) return@mapNotNull null
        val at = parts[0].toLongOrNull() ?: return@mapNotNull null
        val count = parts[1].toIntOrNull() ?: return@mapNotNull null
        if (count <= 0) return@mapNotNull null
        DeletionStamp(at, count)
    }
}

fun formatDeletionStamps(stamps: List<DeletionStamp>): String =
    stamps.joinToString(";") { "${it.atMillis}:${it.count}" }

