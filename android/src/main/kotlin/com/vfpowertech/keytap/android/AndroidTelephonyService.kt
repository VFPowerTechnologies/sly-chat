package com.vfpowertech.keytap.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.telephony.TelephonyManager
import com.vfpowertech.keytap.services.ui.PlatformTelephonyService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map

class AndroidTelephonyService(private val context: Context) : PlatformTelephonyService {
    //TODO validation+formatting
    private fun getPhoneNumber(): String? {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val phoneNumber = telephonyManager.line1Number
        if (phoneNumber == null || phoneNumber.isEmpty())
            return null
        return phoneNumber
    }

    override fun getDevicePhoneNumber(): Promise<String?, Exception> {
        val androidApp = AndroidApp.get(context)

        return if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            Promise.ofSuccess(getPhoneNumber())
        }
        else {
            androidApp.requestPermission(Manifest.permission.READ_PHONE_STATE) map { granted ->
                if (granted)
                    getPhoneNumber()
                else
                    null
            }
        }
    }
}