package com.brian.dialbuddy.telecom

import android.content.Context
import android.telephony.PhoneNumberUtils

/**
 * One-shot permit for a number this app is about to dial.
 * [com.brian.dialbuddy.telecom.OutgoingRedirect] consumes it so other apps cannot place calls
 * while this app is the default Phone app, except emergencies.
 */
object OutgoingAllowance {
    private val lock = Any()
    private var permitted: String? = null

    fun permit(number: String) {
        synchronized(lock) {
            permitted = number
        }
    }

    fun clear() {
        synchronized(lock) {
            permitted = null
        }
    }

    fun consume(context: Context, number: String): Boolean {
        synchronized(lock) {
            val allowed = permitted
            permitted = null
            return allowed != null && PhoneNumberUtils.compare(context, allowed, number)
        }
    }
}
