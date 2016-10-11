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
import rx.subscriptions.CompositeSubscription
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
    override val onlineStatus: Observable<Boolean>
        get() = onlineStatusSubject

    private val eventsSubject = PublishSubject.create<RelayClientEvent>()

    override val events: Observable<RelayClientEvent>
        get() = eventsSubject

    private val clockDifferenceSubject = BehaviorSubject.create<Long>()
    override val clockDifference: Observable<Long>
        get() = clockDifferenceSubject

    override var connectionTag: Int = 0
        private set

    private var clientSubscriptions = CompositeSubscription()

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
        if (!isOnline) {
            relayClient = null
            clientSubscriptions.clear()
        }
        else
            resetConnectionTag()

        onlineStatusSubject.onNext(isOnline)
    }

    private fun onClientError(e: Throwable) {
        log.error("Client error occured: {}", e.message, e)
        setOnlineStatus(false)
    }

    override fun connect(userCredentials: UserCredentials) {
        if (relayClient != null)
            error("Already connected")

        log.info("Attempting to connect to relay")

        val client = relayClientFactory.createClient(userCredentials)

        clientSubscriptions.add(
            client.events
                .observeOn(scheduler)
                .subscribe(object : Observer<RelayClientEvent> {
                    override fun onCompleted() {
                        this@RelayClientManagerImpl.onClientCompleted()
                    }

                    //not getting here for some reason
                    override fun onNext(event: RelayClientEvent) {
                        when (event) {
                            //we only mark the relay connection as usable once authentication has completed
                            is AuthenticationSuccessful -> setOnlineStatus(true)

                            is AuthenticationFailure -> setOnlineStatus(false)

                            is ConnectionLost -> setOnlineStatus(false)

                            is ConnectionFailure -> setOnlineStatus(false)
                        }

                        this@RelayClientManagerImpl.eventsSubject.onNext(event)
                    }

                    //will never be called
                    override fun onError(e: Throwable) {
                        this@RelayClientManagerImpl.onClientError(e)
                    }
                })
        )

        clientSubscriptions.add(
            client.clockDifference
                .observeOn(scheduler)
                .subscribe { onClockDifferenceUpdate(it) }
        )

        relayClient = client

        client.connect()
    }

    private fun onClockDifferenceUpdate(diff: Long) {
        clockDifferenceSubject.onNext(diff)
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

    override val state: RelayClientState
        get() = relayClient?.state ?: RelayClientState.DISCONNECTED
}