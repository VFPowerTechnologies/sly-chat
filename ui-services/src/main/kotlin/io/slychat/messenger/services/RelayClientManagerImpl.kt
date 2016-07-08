package io.slychat.messenger.services

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.relay.*
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Observer
import rx.Scheduler
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.*

class RelayClientManagerImpl(
    private val scheduler: Scheduler,
    private val relayClientFactory: RelayClientFactory
) : RelayClientManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private var relayClient: RelayClient? = null
    override var isOnline: Boolean = false
        private set

    private val onlineStatusSubject = BehaviorSubject.create(isOnline)
    override val onlineStatus: Observable<Boolean> = onlineStatusSubject

    private val eventsSubject = PublishSubject.create<RelayClientEvent>()

    override val events: Observable<RelayClientEvent> = eventsSubject

    override var connectionTag: Int = 0
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

    override fun connect(userCredentials: UserCredentials) {
        if (relayClient != null)
            error("Already connected")

        log.info("Attempting to connect to relay")

        val client = relayClientFactory.createClient(userCredentials)

        client.events
            .observeOn(scheduler)
            .subscribe(object : Observer<RelayClientEvent> {
                override fun onCompleted() {
                    this@RelayClientManagerImpl.onClientCompleted()
                }

                override fun onNext(event: RelayClientEvent) {
                    when (event) {
                        //we only mark the relay connection as usable once authentication has completed
                        is AuthenticationSuccessful -> setOnlineStatus(true)
                        is ConnectionLost -> setOnlineStatus(false)
                        is ConnectionFailure -> relayClient = null
                    }

                    this@RelayClientManagerImpl.eventsSubject.onNext(event)
                }

                //will never be called
                override fun onError(e: Throwable) {
                    this@RelayClientManagerImpl.onClientError(e)
                }

            })

        relayClient = client

        client.connect()
    }

    override fun disconnect() {
        val relayClient = relayClient ?: return
        relayClient.disconnect()
    }

    private fun getClientOrThrow(): RelayClient {
        return relayClient ?: throw NoClientException()
    }

    override fun sendMessage(connectionTag: Int, to: UserId, messageBundle: RelayMessageBundle, messageId: String) {
        val relayClient = getClientOrThrow()

        if (connectionTag != this.connectionTag) {
            log.debug("Dropping message {} to {} due to connection tag mismatch", messageId, to.long)
            return
        }

        relayClient.sendMessage(to, messageBundle, messageId)
    }

    override fun sendMessageReceivedAck(messageId: String) {
        val relayClient = getClientOrThrow()

        relayClient.sendMessageReceivedAck(messageId)
    }

    override fun sendPing() {
        getClientOrThrow().sendPing()
    }

    override val state: RelayClientState?
        get() = relayClient?.state
}