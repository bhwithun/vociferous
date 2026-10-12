package com.brian.dialbuddy.telecom

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.VoicemailContract
import com.brian.dialbuddy.domain.VoicemailEntry

class VoicemailAccessDenied(message: String) : SecurityException(message)

data class VoicemailMedia(
    val hasContent: Boolean,
    val sourcePackage: String?,
)

class VoicemailStore(private val resolver: ContentResolver) {
    fun load(): List<VoicemailEntry> {
        try {
            return query(FULL_PROJECTION)
        } catch (_: IllegalArgumentException) {
            return query(MIN_PROJECTION)
        } catch (error: SecurityException) {
            throw VoicemailAccessDenied(error.message ?: "Voicemail access denied")
        }
    }

    fun media(id: Long): VoicemailMedia {
        val uri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
        try {
            resolver.query(uri, MEDIA_PROJECTION, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return VoicemailMedia(hasContent = false, sourcePackage = null)
                val contentIndex = cursor.getColumnIndex(VoicemailContract.Voicemails.HAS_CONTENT)
                val packageIndex = cursor.getColumnIndex(VoicemailContract.Voicemails.SOURCE_PACKAGE)
                val hasContent = contentIndex >= 0 && !cursor.isNull(contentIndex) && cursor.getInt(contentIndex) == 1
                val source = if (packageIndex < 0) null else cursor.getString(packageIndex)
                return VoicemailMedia(hasContent, source)
            }
            return VoicemailMedia(hasContent = false, sourcePackage = null)
        } catch (_: IllegalArgumentException) {
            return VoicemailMedia(hasContent = true, sourcePackage = null)
        } catch (error: SecurityException) {
            throw VoicemailAccessDenied(error.message ?: "Voicemail access denied")
        }
    }

    fun delete(id: Long): Boolean {
        val uri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
        try {
            return resolver.delete(uri, null, null) > 0
        } catch (error: SecurityException) {
            throw VoicemailAccessDenied(error.message ?: "Voicemail delete denied")
        }
    }

    fun itemUri(id: Long, sourcePackage: String?): Uri {
        val base = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
        if (sourcePackage.isNullOrBlank()) return base
        return base.buildUpon()
            .appendQueryParameter(VoicemailContract.Voicemails.SOURCE_PACKAGE, sourcePackage)
            .build()
    }

    private fun query(projection: Array<String>): List<VoicemailEntry> {
        val cursor = resolver.query(
            VoicemailContract.Voicemails.CONTENT_URI,
            projection,
            null,
            null,
            "${VoicemailContract.Voicemails.DATE} DESC",
        ) ?: return emptyList()
        cursor.use { rows ->
            val idIndex = rows.getColumnIndexOrThrow(VoicemailContract.Voicemails._ID)
            val numberIndex = rows.getColumnIndex(VoicemailContract.Voicemails.NUMBER)
            val dateIndex = rows.getColumnIndex(VoicemailContract.Voicemails.DATE)
            val durationIndex = rows.getColumnIndex(VoicemailContract.Voicemails.DURATION)
            val archivedIndex = rows.getColumnIndex(VoicemailContract.Voicemails.ARCHIVED)
            val deletedIndex = rows.getColumnIndex(DELETED)
            val entries = ArrayList<VoicemailEntry>(rows.count)
            while (rows.moveToNext()) {
                val duration = if (durationIndex < 0 || rows.isNull(durationIndex)) {
                    null
                } else {
                    rows.getInt(durationIndex)
                }
                entries += VoicemailEntry(
                    id = rows.getLong(idIndex),
                    number = if (numberIndex < 0) null else rows.getString(numberIndex),
                    durationSeconds = duration,
                    dateMillis = if (dateIndex < 0) 0L else rows.getLong(dateIndex),
                    archived = archivedIndex >= 0 && rows.getInt(archivedIndex) == 1,
                    deleted = deletedIndex >= 0 && !rows.isNull(deletedIndex) && rows.getInt(deletedIndex) == 1,
                )
            }
            return entries
        }
    }

    private companion object {
        const val DELETED = "deleted"
        val FULL_PROJECTION = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION,
            VoicemailContract.Voicemails.ARCHIVED,
            DELETED,
        )
        val MIN_PROJECTION = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION,
        )
        val MEDIA_PROJECTION = arrayOf(
            VoicemailContract.Voicemails.HAS_CONTENT,
            VoicemailContract.Voicemails.SOURCE_PACKAGE,
        )
    }
}
