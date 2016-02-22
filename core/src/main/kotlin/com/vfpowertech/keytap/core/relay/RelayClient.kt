package com.vfpowertech.keytap.core.relay

import com.vfpowertech.keytap.core.relay.CommandCode.CLIENT_SEND_MESSAGE
import com.vfpowertech.keytap.core.relay.CommandCode.SERVER_MESSAGE_RECEIVED
import com.vfpowertech.keytap.core.relay.CommandCode.SERVER_MESSAGE_SENT
import com.vfpowertech.keytap.core.relay.CommandCode.SERVER_REGISTER_REQUEST
import com.vfpowertech.keytap.core.relay.CommandCode.SERVER_REGISTER_SUCCESSFUL
import com.vfpowertech.keytap.core.relay.CommandCode.SERVER_USER_OFFLINE
import com.vfpowertech.keytap.core.relay.RelayClientState.AUTHENTICATED
import com.vfpowertech.keytap.core.relay.RelayClientState.AUTHENTICATING
import com.vfpowertech.keytap.core.relay.RelayClientState.CONNECTED
import com.vfpowertech.keytap.core.relay.RelayClientState.CONNECTING
import com.vfpowertech.keytap.core.relay.RelayClientState.DISCONNECTED
import com.vfpowertech.keytap.core.relay.RelayClientState.DISCONNECTING
import org.slf4j.LoggerFactory
import rx.Observer
import rx.Scheduler
import java.net.InetSocketAddress

/**
 * Higher-level abstraction over a relay server connection.
 *
 * Must only be used on the main app thread. Once disconnected, cannot be reused.
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

    //TODO expose observable

    private fun onNext(message: ServerMessage) {
        when (message) {
            is RelayConnectionEstablished -> {
                relayConnection = message.connection
                state = CONNECTED
                log.info("Relay connection established")
                authenticate()
            }

            is RelayConnectionLost -> {
                log.info("Relay connection lost")
            }

            is RelayMessage -> handleRelayMessage(message)
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
        log.info("Authenticating as {}", credentials.username)
        val connection = getConnectionOrThrow()
        connection.sendMessage(createAuthRequest(credentials))
        state = AUTHENTICATING
    }

    /** Handles all incoming relay messages, updating internal state as necessary. */
    private fun handleRelayMessage(message: RelayMessage) {
        when (message.header.commandCode) {
            SERVER_REGISTER_SUCCESSFUL -> {
                log.info("Registration successful")
                state = AUTHENTICATED
            }

            SERVER_REGISTER_REQUEST -> {
                if (state == AUTHENTICATING) {
                    log.info("Authentication failed, disconnecting")
                    //TODO send error
                    disconnect()
                }
                else {
                    log.info("Authentication expired, renewing")
                    //TODO still need to disconnect since the web api handles auth
                }
            }

            SERVER_MESSAGE_SENT -> {
                log.info(
                    "Message <{}> to <<{}>> has been successfully sent",
                    message.header.messageId,
                    message.header.toUserEmail
                )
            }

            SERVER_MESSAGE_RECEIVED -> {
                log.info(
                    "Server has received message <{}> to <<{}>>",
                    message.header.messageId,
                    message.header.toUserEmail
                )
            }

            CLIENT_SEND_MESSAGE -> {
                log.info(
                    "Received message <{}> from <<{}>>",
                    message.header.messageId,
                    message.header.toUserEmail
                )
            }

            SERVER_USER_OFFLINE -> {
                log.info(
                    "User {} is offline, unable to send message <{}>",
                    message.header.toUserEmail,
                    message.header.messageId
                )
            }

            else -> {
                log.warn("Unhandled message type: {}", message.header.commandCode)
            }
        }
    }

    private fun onCompleted() {
        log.info("Connection closed")
        state = DISCONNECTED
    }

    private fun onError(e: Throwable) {
        log.error("Relay error", e)
        state = DISCONNECTED
    }

    fun connect() {
        connector.connect(serverAddress)
            .observeOn(scheduler)
            .subscribe(object : Observer<ServerMessage> {
                override fun onCompleted() {
                    this@RelayClient.onCompleted()
                }

                override fun onNext(message: ServerMessage) {
                    this@RelayClient.onNext(message)
                }

                override fun onError(e: Throwable) {
                    this@RelayClient.onError(e)
                }
            })

        state = CONNECTING
    }

    fun disconnect() {
        val connection = getConnectionOrThrow()
        connection.disconnect()
        state = DISCONNECTING
        wasDisconnectRequested = true
    }

    fun sendMessage(to: String, message: String) {
        log.info("Sending message <<{}>> to <<{}>>", message, to)
        val connection = getAuthConnectionOrThrow()
        val messageId = "5248a1d7dc7e300ef2e18e30a6731455"
        connection.sendMessage(createSendMessageMessage(credentials, to, message, messageId))
    }
}