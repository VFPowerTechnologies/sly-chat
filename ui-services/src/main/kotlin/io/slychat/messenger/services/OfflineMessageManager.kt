package io.slychat.messenger.services

import io.slychat.messenger.core.http.api.offline.OfflineMessagesAsyncClient
import io.slychat.messenger.core.http.api.offline.OfflineMessagesClearRequest
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.messaging.MessengerService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.alwaysUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription

class OfflineMessageManager(
    networkAvailable: Observable<Boolean>,
    private val offlineMessagesClient: OfflineMessagesAsyncClient,
    private val messengerService: MessengerService,
    private val authTokenManager: AuthTokenManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private var scheduled = false

    private var running = false

    private var isOnline = false

    private val networkAvailableSubscription: Subscription

    init {
        networkAvailableSubscription = networkAvailable.subscribe { status ->
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

        scheduled = false
        running = true

        log.info("Fetching offline messages")

        authTokenManager.bind { userCredentials ->
            offlineMessagesClient.get(userCredentials) bindUi { response ->
                if (response.messages.isNotEmpty()) {
                    val offlineMessages = response.messages.map { m ->
                        Package(PackageId(m.from, randomUUID()), m.timestamp, m.serializedMessage)
                    }

                    messengerService.addOfflineMessages(offlineMessages) bind {
                        offlineMessagesClient.clear(userCredentials, OfflineMessagesClearRequest(response.range))
                    }
                } else
                    Promise.ofSuccess(Unit)
            }
        } fail { e ->
            log.error("Unable to fetch offline messages: {}", e.message, e)
        } alwaysUi {
            running = false
        }
    }

    fun shutdown() {
        networkAvailableSubscription.unsubscribe()
    }
}