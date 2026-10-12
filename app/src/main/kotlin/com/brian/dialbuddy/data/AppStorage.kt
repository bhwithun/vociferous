package com.brian.dialbuddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppStorage(private val context: Context) {

    object Keys {
        val THRESHOLD = intPreferencesKey("threshold_seconds")
        val DELETION_STAMPS = stringPreferencesKey("deletion_stamps")
        val DELETED_TOTAL = longPreferencesKey("deleted_total")
        val DELETED_KNOWN = longPreferencesKey("deleted_known")
        val DELETED_UNKNOWN = longPreferencesKey("deleted_unknown")
        val DELETED_BULK = longPreferencesKey("deleted_bulk")
        val SPEAKER = booleanPreferencesKey("speakerphone")
    }

    suspend fun getStringOnce(key: Preferences.Key<String>, default: String = ""): String =
        context.dataStore.data.first()[key] ?: default

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getIntOnce(key: Preferences.Key<Int>, default: Int): Int =
        context.dataStore.data.first()[key] ?: default

    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getLongOnce(key: Preferences.Key<Long>, default: Long = 0L): Long =
        context.dataStore.data.first()[key] ?: default

    suspend fun setLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getBooleanOnce(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean =
        context.dataStore.data.first()[key] ?: default

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
