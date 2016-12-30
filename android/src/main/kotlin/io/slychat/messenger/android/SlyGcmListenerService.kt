package io.slychat.messenger.android

import android.os.Bundle
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.gms.gcm.GcmListenerService
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.pushnotifications.OfflineMessageInfo
import io.slychat.messenger.core.pushnotifications.OfflineMessagesPushNotification
import io.slychat.messenger.core.typeRef
import org.slf4j.LoggerFactory

class SlyGcmListenerService : GcmListenerService() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessageReceived(from: String, data: Bundle) {
        log.debug("Received message from server")

        val type = data.getString("type")
        if (type != OfflineMessagesPushNotification.TYPE) {
            log.warn("Received unsupported GCM message type: $type")
            return
        }

        try {
            handleMessage(data)
        }
        catch (e: Exception) {
            log.error("Unable to deserialize GCM message: {}", e, e.message)
        }
    }

    private fun handleMessage(data: Bundle) {
        val version = data.getString("version").toInt()
        if (version != 1) {
            log.warn("Unsupported version for offline messages: {}", version)
            return
        }

        val infoSerialized = data.getString("info")
        val objectMapper = ObjectMapper()
        val info: List<OfflineMessageInfo> = objectMapper.readValue(infoSerialized, typeRef<List<OfflineMessageInfo>>())

        val accountStr = data.getString("account")
        val account = SlyAddress.fromString(accountStr) ?: throw RuntimeException("Invalid account address format: $accountStr")

        val accountName = data.getString("accountName")

        val message = OfflineMessagesPushNotification(account, accountName, info)

        val app = AndroidApp.get(this)
        androidRunInMain(this) {
            app.onGCMMessage(message)
        }
    }
}