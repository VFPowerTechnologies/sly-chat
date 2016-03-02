package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.relay.*
import com.vfpowertech.keytap.services.di.UserComponent
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Observer
import rx.Scheduler
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

/**
 * Wrapper around a relay connection.
 *
 * Lives only so long as the user's session does.
 *
 * May be online or offline.
 *
 * All exposed observables fire on the main thread.
 */
class RelayClientManager(
    val scheduler: Scheduler,
    val userComponent: UserComponent
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private var relayClient: RelayClient? = null
    var isOnline: Boolean = false
        private set

    private val onlineStatusSubject = BehaviorSubject.create(isOnline)
    /** Fires updates on relay connection status updates. */
    val onlineStatus: Observable<Boolean> = onlineStatusSubject

    private val eventsSubject = PublishSubject.create<RelayClientEvent>()
    /** Received relay messages. */
    val events: Observable<RelayClientEvent> = eventsSubject

    private fun onClientCompleted() {
        log.debug("Relay event observable completed")
        setOnlineStatus(false)
    }

    private fun setOnlineStatus(isOnline: Boolean) {
        log.info("Relay is online: {}", isOnline)
        this.isOnline = isOnline
        if (!isOnline)
            relayClient = null

        onlineStatusSubject.onNext(isOnline)
    }

    private fun onClientError(e: Throwable) {
        log.error("Relay observable error", e)
        setOnlineStatus(false)
    }

    /** Connect to the relay. */
    fun connect() {
        if (relayClient != null)
            error("Already connected")

        log.info("Attempting to connect to relay")

        val loginData = userComponent.userLoginData
        val authToken = loginData.authToken ?: throw ReauthenticationRequiredException()

        val client = userComponent.createRelayClient()

        client.events
            .observeOn(scheduler)
            .subscribe(object : Observer<RelayClientEvent> {
                override fun onCompleted() {
                    this@RelayClientManager.onClientCompleted()
                }

                override fun onNext(event: RelayClientEvent) {
                    when (event) {
                        is ConnectionEstablished -> setOnlineStatus(true)
                        is ConnectionLost -> setOnlineStatus(false)
                        is ConnectionFailure -> relayClient = null
                    }

                    this@RelayClientManager.eventsSubject.onNext(event)
                }

                override fun onError(e: Throwable) {
                    this@RelayClientManager.onClientError(e)
                }

            })

        relayClient = client

        client.connect()
    }

    fun disconnect() {
        val relayClient = relayClient ?: return
        relayClient.disconnect()
    }

    private fun getClientOrThrow(): RelayClient {
        return relayClient ?: throw NoClientException()
    }

    fun sendMessage(to: String, message: String, messageId: String) {
        val relayClient = getClientOrThrow()

        relayClient.sendMessage(to, message, messageId)
    }

}