package io.slychat.messenger.android

import android.os.Bundle
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.gms.gcm.GcmListenerService
import org.slf4j.LoggerFactory

data class OfflineMessageInfo(
    @JsonProperty("username")
    val username: String,
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

        //TODO version check and upgrade to newer versions
        val version = data.getString("version").toInt()

        val infoSerialized = data.getString("info")
        val account = data.getString("account")

        val app = AndroidApp.get(this)
        androidRunInMain(this) {
            app.onGCMMessage(account)
        }
    }
}