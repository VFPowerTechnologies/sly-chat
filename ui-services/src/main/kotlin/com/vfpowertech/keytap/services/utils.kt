@file:JvmName("UIServicesUtils")
package com.vfpowertech.keytap.services

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.successUi

fun parsePhoneNumber(s: String, defaultRegion: String): Phonenumber.PhoneNumber? {
    val phoneNumberUtil = PhoneNumberUtil.getInstance()

    try {
        //assume this is a valid international number
        if (s.startsWith("+"))
            return phoneNumberUtil.parse(s, null)
        else {
            val region = defaultRegion
            //pretty sure this can't (normally) be empty on android
            val phoneNumber = phoneNumberUtil.parse(s, region)

            //we don't bother checking isValid; it's slow and somewhat pointless
            return phoneNumber
        }
    }
    catch (e: NumberParseException) {
        return null
    }
}

infix fun <V, V2> Promise<V, Exception>.mapUi(body: (V) -> V2): Promise<V2, Exception> {
    val deferred = deferred<V2, Exception>()

    this successUi {
        try {
            deferred.resolve(body(it))
        }
        catch (e: Exception) {
            deferred.reject(e)
        }
    }

    this fail { e ->
        deferred.reject(e)
    }

    return deferred.promise
}