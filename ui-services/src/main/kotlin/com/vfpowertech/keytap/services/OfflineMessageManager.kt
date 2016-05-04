package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.http.api.offline.OfflineMessagesAsyncClient
import com.vfpowertech.keytap.core.http.api.offline.OfflineMessagesClearRequest
import com.vfpowertech.keytap.core.http.api.offline.OfflineMessagesGetRequest
import com.vfpowertech.keytap.services.crypto.deserializeEncryptedMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.alwaysUi
import org.slf4j.LoggerFactory

class OfflineMessageManager(
    private val application: KeyTapApplication,
    private val userLoginData: UserLoginData,
    private val serverUrl: String,
    private val messengerService: MessengerService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private var scheduled = false

    private var running = false

    private var isOnline = false

    init {
        application.networkAvailable.subscribe { status ->
            isOnline = status
            if (status && scheduled)
                fetch()
        }
    }

    fun fetch() {
        if (isOnline == false) {
            scheduled = true
            return
        }

        if (running)
            return

        val authToken = userLoginData.authToken ?: return

        scheduled = false
        running = true

        log.info("Fetching offline messages")

        val offlineMessagesClient = OfflineMessagesAsyncClient(serverUrl)
        offlineMessagesClient.get(OfflineMessagesGetRequest(authToken)) bindUi { response ->
            if (response.messages.isNotEmpty()) {
                //TODO move this elsewhere?
                val offlineMessages = response.messages.map { m ->
                    val encryptedMessage = deserializeEncryptedMessage(m.serializedMessage)
                    OfflineMessage(m.from, m.timestamp, encryptedMessage)
                }

                messengerService.addOfflineMessages(offlineMessages) bind {
                    offlineMessagesClient.clear(OfflineMessagesClearRequest(authToken, response.range))
                }
            }
            else
                Promise.ofSuccess(Unit)
        } fail { e ->
            log.error("Unable to fetch offline messages: {}", e.message, e)
        } alwaysUi {
            running = false
        }
    }
}