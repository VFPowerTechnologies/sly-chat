package com.vfpowertech.keytap.testclients

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.relay.*
import com.vfpowertech.keytap.core.relay.base.netty.NettyRelayConnector
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType.ERROR
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority.ALWAYS
import javafx.scene.layout.Priority.NEVER
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import rx.Observer
import rx.schedulers.JavaFxScheduler
import java.net.ConnectException
import java.net.InetSocketAddress

class MainWindow : VBox() {
    val connectBtn: Button
    val disconnectBtn: Button
    val chatInput: TextField

    val loginUsername: TextField
    val sendToUsername: TextField

    val chatMessages: TextArea

    init {
        val buttonBox = HBox()
        children.add(buttonBox)
        setVgrow(buttonBox, NEVER)

        chatInput = TextField("message")
        buttonBox.children.add(chatInput)
        HBox.setHgrow(chatInput, ALWAYS)

        connectBtn = Button("Connect")
        buttonBox.children.add(connectBtn)

        disconnectBtn = Button("Disconnect")
        disconnectBtn.isDisable = true
        buttonBox.children.add(disconnectBtn)

        val usernamesBox = HBox()
        children.add(usernamesBox)

        loginUsername = TextField("1")
        usernamesBox.children.add(loginUsername)

        val swapBtn = Button("<->")
        usernamesBox.children.add(swapBtn)

        sendToUsername = TextField("2")
        usernamesBox.children.add(sendToUsername)

        swapBtn.setOnAction {
            val login = loginUsername.text
            val to = sendToUsername.text
            loginUsername.text = to
            sendToUsername.text = login
        }

        chatMessages = TextArea()
        chatMessages.isEditable = false
        children.add(chatMessages)
        setVgrow(chatMessages, ALWAYS)
    }
}

class App : Application() {
    private lateinit var mainWindow: MainWindow
    private val log = LoggerFactory.getLogger(javaClass)
    private val authToken = "244e802de8fc643955752b82f0bc0db"
    private val relayAddress = InetSocketAddress("localhost", 2153)
    private var relayClient: RelayClient? = null

    private fun toggleConnectButtons(isConnected: Boolean) {
        mainWindow.connectBtn.isDisable = isConnected
        mainWindow.disconnectBtn.isDisable = !isConnected
    }

    private fun onNext(event: RelayClientEvent) {
        when (event) {
            is ConnectionEstablished -> {
            }

            is AuthenticationFailure -> {
                val alert = Alert(ERROR)
                alert.title = "Authentication failed"
                alert.headerText = "Invalid username or password."

                alert.showAndWait()
            }

            is AuthenticationSuccessful -> {

            }

            is AuthenticationExpired -> {
                log.info("Authentication expired, need reauth")
            }

            is ReceivedMessage -> {
                val v = ObjectMapper().readValue(event.content, Map::class.java)
                mainWindow.chatMessages.appendText("${event.from}> ${v["message"]}\n")
            }

            is UserOffline -> {
                mainWindow.chatMessages.appendText("> ${event.to} is currently offline\n")
            }

            else -> {
                log.warn("Unhandled RelayClientEvent: {}", event)
            }
        }
    }

    private fun onComplete() {
        log.info("Client observable complete")

        relayClient = null

        toggleConnectButtons(false)
    }

    private fun onError(e: Throwable) {
        if (e is ConnectException) {
            val alert = Alert(ERROR)
            alert.title = "Connection error"
            alert.headerText = "Unable to connect to relay server"
            alert.contentText = e.message

            alert.showAndWait()
        }
        else {
            log.error("Error from client connection", e)
        }

        toggleConnectButtons(false)
    }

    private fun launchConnection(username: String) {
        val a = authToken + username[0]
        //val a = authToken
        assert(relayClient == null)
        val client = RelayClient(
            NettyRelayConnector(),
            JavaFxScheduler.getInstance(),
            relayAddress,
            UserCredentials(username, a)
        )
        client.events
            .observeOn(JavaFxScheduler.getInstance())
            .subscribe(object : Observer<RelayClientEvent> {
                override fun onNext(event: RelayClientEvent) {
                    this@App.onNext(event)
                }

                override fun onCompleted() {
                    this@App.onComplete()
                }

                override fun onError(e: Throwable) {
                    this@App.onError(e)
                }
            })

        client.connect()
        relayClient = client

        toggleConnectButtons(true)
    }

    override fun start(stage: Stage) {
        mainWindow = MainWindow()

        val chatInput = mainWindow.chatInput
        chatInput.setOnAction {
            val t = chatInput.text
            if (!t.isEmpty()) {
                chatInput.text = ""
                mainWindow.chatMessages.appendText("me> $t\n")
                val messageId = "5248a1d7dc7e300ef2e18e30a6731455"
                val content = ObjectMapper().writeValueAsBytes(mapOf("message" to t))
                relayClient!!.sendMessage(UserId(mainWindow.sendToUsername.text.toLong()), content, messageId)
            }
        }

        mainWindow.connectBtn.setOnAction {
            launchConnection(mainWindow.loginUsername.text)
        }

        mainWindow.disconnectBtn.setOnAction {
            relayClient!!.disconnect()
        }

        stage.scene = Scene(mainWindow, 785.0, 330.0)
        stage.show()
    }

    override fun stop() {
        println("Quitting")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(App::class.java, *args)
        }
    }
}
