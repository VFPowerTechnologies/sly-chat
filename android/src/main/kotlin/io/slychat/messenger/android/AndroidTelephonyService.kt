package io.slychat.messenger.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.services.PlatformTelephonyService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class AndroidTelephonyService(private val context: Context) : PlatformTelephonyService {
    private val log = LoggerFactory.getLogger(javaClass)

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

    override fun supportsMakingCalls(): Boolean {
        return true
    }

    private fun fireCallIntent(androidApp: AndroidApp, contactInfo: ContactInfo) {
        androidApp.requestPermission(Manifest.permission.CALL_PHONE) successUi { granted ->
            if (granted) {
                val intent = Intent(Intent.ACTION_CALL)
                intent.data = Uri.parse("tel:${contactInfo.phoneNumber}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            else
                log.info("User denied CALL_PHONE permission, doing nothing")
        } fail {
            log.error("Failure requesting CALL_PHONE permission: {}", it.message, it)
        }
    }

    override fun callContact(userId: UserId) {
        val androidApp = AndroidApp.get(context)

        val userComponent = androidApp.app.userComponent ?: error("Not logged in")

        userComponent.contactsService.get(userId) successUi { contactInfo ->
            if (contactInfo != null && contactInfo.phoneNumber != null)
                fireCallIntent(androidApp, contactInfo)
        } fail {
            log.error("Error while retrieving contact id {}: {}", userId, it.message, it)
        }
    }
}