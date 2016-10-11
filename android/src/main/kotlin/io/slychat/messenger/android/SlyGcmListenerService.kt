package io.slychat.messenger.android

import android.os.Bundle
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.gms.gcm.GcmListenerService
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.typeRef
import org.slf4j.LoggerFactory

data class OfflineMessageInfo(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("pendingCount")
    val pendingCount: Int
)

val GCM_TYPE_OFFLINE_MESSAGE = "offline-message"

class SlyGcmListenerService : GcmListenerService() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessageReceived(from: String, data: Bundle) {
        log.debug("Received message from server")

        val type = data.getString("type")
        if (type != GCM_TYPE_OFFLINE_MESSAGE) {
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
        //TODO version check and upgrade to newer versions
        val version = data.getString("version").toInt()

        val infoSerialized = data.getString("info")
        val objectMapper = ObjectMapper()
        val info: List<OfflineMessageInfo> = objectMapper.readValue(infoSerialized, typeRef<List<OfflineMessageInfo>>())

        val accountStr = data.getString("account")
        val account = SlyAddress.fromString(accountStr) ?: throw RuntimeException("Invalid account address format: $accountStr")

        val accountName = data.getString("accountName")

        val app = AndroidApp.get(this)
        androidRunInMain(this) {
            app.onGCMMessage(account, accountName, info)
        }
    }
}