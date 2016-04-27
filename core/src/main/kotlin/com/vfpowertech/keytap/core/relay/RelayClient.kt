package com.vfpowertech.keytap.core.relay

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.relay.RelayClientState.*
import com.vfpowertech.keytap.core.relay.base.*
import com.vfpowertech.keytap.core.relay.base.CommandCode.*
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Observer
import rx.Scheduler
import rx.subjects.PublishSubject
import java.net.InetSocketAddress

private fun String.toUserId(): UserId =
    UserId(toLong())

/**
 * Higher-level abstraction over a relay server connection.
 *
 * Must only be used on the main app thread.
 * Once disconnected, cannot be reused, as the events observable will be closed.
 *
 * Will disconnect on failed authentication.
 *
 * @param credentials
 * @param scheduler Must correspond to a scheduler that runs on the main app thread (eg: JavaFXScheduler). Will be used when observing relay messages.
 */
class RelayClient(
    private val connector: RelayConnector,
    private val scheduler: Scheduler,
    private val serverAddress: InetSocketAddress,
    private val credentials : UserCredentials
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var relayConnection: RelayConnection? = null
    private var state = DISCONNECTED
    private var wasDisconnectRequested = false
    private val publishSubject = PublishSubject.create<RelayClientEvent>()

    /** Client event stream. Will never call onError; check ConnectionLost.error instead. */
    val events: Observable<RelayClientEvent> = publishSubject

    private fun onNext(event: RelayConnectionEvent) {
        when (event) {
            is RelayConnectionEstablished -> {
                relayConnection = event.connection
                state = CONNECTED
                log.info("Relay connection established")
                authenticate()
                emitEvent(ConnectionEstablished())
            }

            is RelayConnectionLost -> {
                log.info("Relay connection lost")
            }

            is RelayMessage -> handleRelayMessage(event)
        }
    }

    /** Throws NotConnectedException if not connected. */
    private fun getConnectionOrThrow(): RelayConnection {
        return relayConnection ?: throw NotConnectedException()
    }

    /** Throws NotConnectedException if not connected, or NotAuthenticatedException if not authenticated. */
    private fun getAuthConnectionOrThrow(): RelayConnection {
        val connection = getConnectionOrThrow()
        if (state != AUTHENTICATED)
            throw NotAuthenticatedException(state)
        return connection
    }

    private fun authenticate() {
        log.info("Authenticating as {}", credentials.address)
        val connection = getConnectionOrThrow()
        connection.sendMessage(createAuthRequest(credentials))
        state = AUTHENTICATING
    }

    private fun emitEvent(ev: RelayClientEvent) {
        publishSubject.onNext(ev)
    }

    /** Handles all incoming relay messages, updating internal state as necessary. */
    private fun handleRelayMessage(message: RelayMessage) {
        when (message.header.commandCode) {
            SERVER_REGISTER_SUCCESSFUL -> {
                log.info("Registration successful")
                state = AUTHENTICATED

                emitEvent(AuthenticationSuccessful())
            }

            SERVER_REGISTER_REQUEST -> {
                if (state == AUTHENTICATING) {
                    log.info("Authentication failed, disconnecting")
                    emitEvent(AuthenticationFailure())
                    disconnect()
                }
                else {
                    //TODO still need to disconnect since the web api handles auth
                    log.info("Authentication expired")
                    emitEvent(AuthenticationExpired())
                    disconnect()
                }
            }

            SERVER_MESSAGE_SENT -> {
                val to = message.header.toUserId
                val messageId = message.header.messageId
                log.info(
                    "Message <{}> to <<{}>> has been successfully sent",
                    messageId,
                    to
                )

                //user was online and message was sent
                //not sure we should bother with this event; client might not view it immediately/etc anyways
                emitEvent(MessageSentToUser(to.toUserId(), messageId))
            }

            SERVER_MESSAGE_RECEIVED -> {
                val to = message.header.toUserId
                val messageId = message.header.messageId
                log.info(
                    "Server has received message <{}> to <<{}>>",
                    messageId,
                    to
                )

                emitEvent(ServerReceivedMessage(to.toUserId(), messageId))
            }

            //when receiving a message of this type, it indicates a new message from someone
            CLIENT_SEND_MESSAGE -> {
                val from = message.header.fromUserId
                val messageId = message.header.messageId
                log.info(
                    "Received message <{}> from <<{}>>",
                    messageId,
                    from
                )

                emitEvent(ReceivedMessage(from.toUserId(), message.content, messageId))
            }

            SERVER_USER_OFFLINE -> {
                val to = message.header.toUserId
                val messageId = message.header.messageId

                log.info(
                    "User {} is offline, unable to send message <{}>",
                    to,
                    messageId
                )

                emitEvent(UserOffline(to.toUserId(), messageId))
            }

            else -> {
                log.warn("Unhandled message type: {}", message.header.commandCode)
            }
        }
    }

    private fun onCompleted() {
        log.info("Connection closed")
        state = DISCONNECTED

        emitEvent(ConnectionLost(wasDisconnectRequested))
        publishSubject.onCompleted()
    }

    private fun onError(e: Throwable) {
        state = DISCONNECTED

        //if the error occured during connection
        if (relayConnection == null)
            publishSubject.onNext(ConnectionFailure(e))
        else {
            log.error("Relay error", e)
            emitEvent(ConnectionLost(wasDisconnectRequested, e))
        }

        publishSubject.onCompleted()
    }

    fun connect() {
        connector.connect(serverAddress)
            .observeOn(scheduler)
            .subscribe(object : Observer<RelayConnectionEvent> {
                override fun onCompleted() {
                    this@RelayClient.onCompleted()
                }

                override fun onNext(event: RelayConnectionEvent) {
                    this@RelayClient.onNext(event)
                }

                override fun onError(e: Throwable) {
                    this@RelayClient.onError(e)
                }
            })

        state = CONNECTING
    }

    fun disconnect() {
        val connection = relayConnection
        if (connection == null) {
            log.warn("Disconnect requested but not connected, ignoring")
            return
        }
        connection.disconnect()
        state = DISCONNECTING
        wasDisconnectRequested = true
    }

    fun sendMessage(to: UserId, content: ByteArray, messageId: String) {
        log.info("Sending message <<{}>> to <<{}>>", messageId, to)
        val connection = getAuthConnectionOrThrow()
        connection.sendMessage(createSendMessageMessage(credentials, to, content, messageId))
    }
}