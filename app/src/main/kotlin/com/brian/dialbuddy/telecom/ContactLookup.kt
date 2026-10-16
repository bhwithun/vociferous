package com.brian.dialbuddy.telecom

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup

data class ContactMatch(
    val name: String?,
    val photoUri: Uri?,
)

object ContactLookup {
    fun findName(context: Context, number: String): String? = find(context, number)?.name

    fun numberFromPick(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(Phone.NUMBER)
            if (index < 0) return null
            return cursor.getString(index)
        }
        return null
    }

    fun viewUri(context: Context, number: String): Uri? {
        if (number.isBlank()) return null
        val lookup = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            lookup,
            arrayOf(PhoneLookup.CONTACT_ID, PhoneLookup.LOOKUP_KEY),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val idIndex = cursor.getColumnIndex(PhoneLookup.CONTACT_ID)
            val keyIndex = cursor.getColumnIndex(PhoneLookup.LOOKUP_KEY)
            if (idIndex < 0 || keyIndex < 0) return null
            val key = cursor.getString(keyIndex) ?: return null
            return ContactsContract.Contacts.getLookupUri(cursor.getLong(idIndex), key)
        }
        return null
    }

    fun find(context: Context, number: String): ContactMatch? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI, PhoneLookup.PHOTO_THUMBNAIL_URI),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val nameIndex = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME)
            val photoIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_URI)
            val thumbIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_THUMBNAIL_URI)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val photo = picture(cursor, photoIndex) ?: picture(cursor, thumbIndex)
            return ContactMatch(name, photo?.let(Uri::parse))
        }
        return null
    }

    private fun picture(cursor: Cursor, index: Int): String? {
        if (index < 0 || cursor.isNull(index)) return null
        return cursor.getString(index)?.takeIf { it.isNotBlank() }
    }
}
