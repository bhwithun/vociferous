package com.brian.dialbuddy.telecom

import android.content.Intent
import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import com.brian.dialbuddy.MainActivity

class OutgoingRedirect : CallRedirectionService() {
    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean,
    ) {
        val number = handle.schemeSpecificPart.orEmpty()
        if (number.isNotBlank() && (isEmergency(number) || OutgoingAllowance.consume(this, number))) {
            placeCallUnmodified()
            return
        }
        if (number.isNotBlank()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_DIAL)
                    .setData(handle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        cancelCall()
    }

    private fun isEmergency(number: String): Boolean {
        val telephony = getSystemService(TelephonyManager::class.java) ?: return false
        return try {
            telephony.isEmergencyNumber(number)
        } catch (_: RuntimeException) {
            false
        }
    }
}
