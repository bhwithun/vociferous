package com.brian.dialbuddy.telecom

import android.content.ContentResolver
import android.provider.CallLog
import com.brian.dialbuddy.domain.CallKind
import com.brian.dialbuddy.domain.CallRecord

class CallLogDenied(message: String) : SecurityException(message)

class CallLogStore(private val resolver: ContentResolver) {
    fun load(limit: Int = RECENT_LIMIT): List<CallRecord> {
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString())
            .build()
        try {
            resolver.query(
                uri,
                PROJECTION,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val presentationIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)
                val records = ArrayList<CallRecord>(cursor.count.coerceAtMost(limit))
                while (cursor.moveToNext() && records.size < limit) {
                    val presentation = if (presentationIndex < 0) {
                        CallLog.Calls.PRESENTATION_ALLOWED
                    } else {
                        cursor.getInt(presentationIndex)
                    }
                    val visible = presentation == CallLog.Calls.PRESENTATION_ALLOWED ||
                        presentation == CallLog.Calls.PRESENTATION_PAYPHONE
                    val number = if (!visible || numberIndex < 0 || cursor.isNull(numberIndex)) {
                        ""
                    } else {
                        cursor.getString(numberIndex).orEmpty()
                    }
                    val cached = if (!visible || nameIndex < 0 || cursor.isNull(nameIndex)) {
                        null
                    } else {
                        cursor.getString(nameIndex)
                    }
                    records.add(
                        CallRecord(
                            id = cursor.getLong(idIndex),
                            number = number,
                            cachedName = cached,
                            kind = callKind(if (typeIndex < 0) 0 else cursor.getInt(typeIndex)),
                            dateMillis = if (dateIndex < 0) 0L else cursor.getLong(dateIndex),
                            durationSeconds = if (durationIndex < 0) 0L else cursor.getLong(durationIndex),
                        ),
                    )
                }
                return records
            }
            return emptyList()
        } catch (error: SecurityException) {
            throw CallLogDenied(error.message ?: "Call history denied")
        }
    }

    companion object {
        const val RECENT_LIMIT = 400

        private val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.NUMBER_PRESENTATION,
        )
    }
}

internal fun callKind(type: Int): CallKind = when (type) {
    CallLog.Calls.INCOMING_TYPE -> CallKind.Incoming
    CallLog.Calls.OUTGOING_TYPE -> CallKind.Outgoing
    CallLog.Calls.MISSED_TYPE -> CallKind.Missed
    CallLog.Calls.REJECTED_TYPE -> CallKind.Rejected
    CallLog.Calls.BLOCKED_TYPE -> CallKind.Blocked
    CallLog.Calls.VOICEMAIL_TYPE -> CallKind.Voicemail
    CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallKind.Incoming
    else -> CallKind.Other
}
