@file:JvmName("UIServicesUtils")
package io.slychat.messenger.services

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import io.slychat.messenger.core.persistence.AccountInfo
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.failUi
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

fun getAccountRegionCode(accountInfo: AccountInfo): String {
    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    val phoneNumber = phoneNumberUtil.parse("+${accountInfo.phoneNumber}", null)
    return phoneNumberUtil.getRegionCodeForCountryCode(phoneNumber.countryCode)
}

fun formatPhoneNumber(phonenumber: Phonenumber.PhoneNumber): String {
    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    return phoneNumberUtil.format(phonenumber, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1)
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

infix fun <V, V2> Promise<V, Exception>.bindUi(body: (V) -> Promise<V2, Exception>): Promise<V2, Exception> {
    val deferred = deferred<V2, Exception>()

    this successUi {
        try {
            body(it) success {
                deferred.resolve(it)
            } fail {
                deferred.reject(it)
            }
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

infix fun <V> Promise<V, Exception>.bindRecoverUi(body: (Exception) -> Promise<V, Exception>): Promise<V, Exception> {
    val d = deferred<V, Exception>()

    successUi { d.resolve(it) }

    failUi { e ->
        try {
            body(e) successUi { d.resolve(it) } failUi { d.reject(it) }
        }
        catch (e: Exception) {
            d.reject(e)
        }
    }

    return d.promise
}

infix inline fun <reified E : Exception, T> Promise<T, Exception>.bindRecoverForUi(crossinline body: (E) -> Promise<T, Exception>): Promise<T, Exception> =
    bindRecoverUi { e ->
        if (e is E) body(e)
        else throw e
    }
