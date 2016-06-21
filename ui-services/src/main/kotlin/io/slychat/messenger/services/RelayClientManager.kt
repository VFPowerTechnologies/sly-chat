package io.slychat.messenger.services

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.relay.base.SendMessageContent
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Observer
import rx.Scheduler
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.*

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
    private val scheduler: Scheduler,
    private val relayClientFactory: RelayClientFactory
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

    /**
     * Random value from [0, Integer.MAX) to id the current connection.
     *
     * Used to prevent messages destined for a previous connection from being sent, in the event of things like message
     * encryption being completed between a disconnect and reconnect.
     */
    var connectionTag: Int = 0
        private set

    private fun resetConnectionTag() {
        connectionTag = Random().nextInt(Integer.MAX_VALUE)
    }

    private fun onClientCompleted() {
        log.debug("Relay event observable completed")
        setOnlineStatus(false)
    }

    private fun setOnlineStatus(isOnline: Boolean) {
        log.info("Relay is online: {}", isOnline)
        this.isOnline = isOnline
        if (!isOnline)
            relayClient = null
        else
            resetConnectionTag()

        onlineStatusSubject.onNext(isOnline)
    }

    private fun onClientError(e: Throwable) {
        setOnlineStatus(false)
    }

    /** Connect to the relay. */
    fun connect(userCredentials: UserCredentials) {
        if (relayClient != null)
            error("Already connected")

        log.info("Attempting to connect to relay")

        val client = relayClientFactory.createClient(userCredentials)

        client.events
            .observeOn(scheduler)
            .subscribe(object : Observer<RelayClientEvent> {
                override fun onCompleted() {
                    this@RelayClientManager.onClientCompleted()
                }

                override fun onNext(event: RelayClientEvent) {
                    when (event) {
                        //we only mark the relay connection as usable once authentication has completed
                        is AuthenticationSuccessful -> setOnlineStatus(true)
                        is ConnectionLost -> setOnlineStatus(false)
                        is ConnectionFailure -> relayClient = null
                    }

                    this@RelayClientManager.eventsSubject.onNext(event)
                }

                //will never be called
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

    fun sendMessage(connectionTag: Int, to: UserId, content: SendMessageContent, messageId: String) {
        val relayClient = getClientOrThrow()

        if (connectionTag != this.connectionTag) {
            log.debug("Dropping message {} to {} due to connection tag mismatch", messageId, to.long)
            return
        }

        relayClient.sendMessage(to, content, messageId)
    }

    fun sendMessageReceivedAck(messageId: String) {
        val relayClient = getClientOrThrow()

        relayClient.sendMessageReceivedAck(messageId)
    }

    fun sendPing() {
        getClientOrThrow().sendPing()
    }

    val state: RelayClientState?
        get() = relayClient?.state
}