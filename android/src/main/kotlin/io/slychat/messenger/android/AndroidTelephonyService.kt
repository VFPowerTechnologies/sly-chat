package io.slychat.messenger.android

import android.Manifest
import android.content.Context
import android.telephony.TelephonyManager
import io.slychat.messenger.services.PlatformTelephonyService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map

class AndroidTelephonyService(private val context: Context) : PlatformTelephonyService {
    //TODO validation+formatting
    private fun getPhoneNumber(): String? {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val phoneNumber = telephonyManager.line1Number
        if (phoneNumber == null || phoneNumber.isEmpty())
            return null
        return if (phoneNumber.startsWith("+"))
            phoneNumber.substring(1)
        else
            phoneNumber
    }

    override fun getDevicePhoneNumber(): Promise<String?, Exception> {
        val androidApp = AndroidApp.get(context)

        return androidApp.requestPermission(Manifest.permission.READ_PHONE_STATE) map { granted ->
            if (granted)
                getPhoneNumber()
            else
                null
        }
    }
}