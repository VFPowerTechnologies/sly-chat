/** Low-level relay protocol. */
@file:JvmName("RelayProtocol")
package com.vfpowertech.keytap.core.relay

import com.vfpowertech.keytap.core.crypto.hexify
import java.util.*

val HEADER_SIZE = 589
//CSP
val SIGNATURE = "CSP"
val SIGNATURE_BYTES = SIGNATURE.toByteArray(Charsets.US_ASCII)

//CLIENT_ indicates a client->server command, SERVER_ server->client
enum class CommandCode(val code: Int) {
    CLIENT_REGISTER_REQUEST(1),
    SERVER_REGISTER_SUCCESSFUL(2),
    //server requesting client to register
    //sent when a client sends a message to the server but hasn't registered or the registration has timed out
    SERVER_REGISTER_REQUEST(3),
    CLIENT_CHECK_VALIDITY(4),
    //if auth is no longer valid, server returns SERVER_REGISTER_REQUEST instead of this
    SERVER_ID_VALID(5),
    //this also doubles as receiving a message when received from the server
    CLIENT_SEND_MESSAGE(6),
    //server has received client message
    SERVER_MESSAGE_RECEIVED(7),
    //server has dispatched client message to target user
    SERVER_MESSAGE_SENT(8),
    //message has been viewed on local device
    CLIENT_MESSAGE_VIEW(9),
    //not used, as the server handles offline message stuff
    SERVER_USER_OFFLINE(10),
    CLIENT_FILE_TRANSFER_REQUEST(11),
    CLIENT_FILE_TRANSFER_ACCEPT(12),
    CLIENT_FILE_TRANSFER_DATA(13),
    CLIENT_FILE_TRANSFER_COMPLETE(14),
    CLIENT_FILE_TRANSFER_CANCEL_OR_REJECT(15);

    companion object {
        private val cachedValues = values()

        fun fromInt(commandCode: Int): CommandCode = try {
            cachedValues[commandCode-1]
        }
        catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("Invalid command code: $commandCode")
        }
    }
}

open class RelayException(message: String?, cause: Throwable?) : RuntimeException("Relay Exception") {
    constructor() : this(null, null)

    constructor(message: String) : this(message, null)

    constructor(cause: Throwable) : this(null, cause)
}

class InvalidHeaderSizeException(val size: Int) : RelayException("Header size expected to be ${HEADER_SIZE}, got $size")
class InvalidHeaderSignatureException(val signature: String) : RelayException("Expected header signatured, got $signature")
class InvalidProtocolVersionException(val version: Int) : RelayException("Unsupported protocol version: $version")
class InvalidPayloadException(val commandCode: Int) : RelayException("Invalid payload for command $commandCode")

val PROTOCOL_VERSION_SIZE = 2
val CONTENT_LENGTH_SIZE = 5
val FROM_USER_TOKEN_SIZE = 32
val FROM_USER_EMAIL_SIZE = 254
val TO_USER_EMAIL_SIZE = 254
val MESSAGE_ID_SIZE = 32
val MESSAGE_NUMBER_SIZE = 2
val MESSAGE_NUMBER_TOTAL_SIZE = 2
val COMMAND_CODE_SIZE = 3

data class Header(
    val version: Int,
    val contentLength: Int,
    //authToken
    val fromUserToken: String,
    val fromUserEmail: String,
    val toUserEmail: String,
    //for tracking replies
    //can probably just use incremental ids for the log? although this won't work if logging is disabled
    val messageId: String,
    val messageFragmentNumber: Int,
    val messageFragmentTotal: Int,
    val commandCode: CommandCode
) {
    init {
        require(fromUserToken.length <= FROM_USER_TOKEN_SIZE) { "fromUserToken: ${fromUserToken.length} > ${FROM_USER_TOKEN_SIZE}" }
        require(fromUserEmail.length <= FROM_USER_EMAIL_SIZE) { "fromUserEmail: ${fromUserEmail.length} > ${FROM_USER_EMAIL_SIZE}" }
        require(toUserEmail.length <= TO_USER_EMAIL_SIZE) { "toUserEmail: ${toUserEmail.length} > ${TO_USER_EMAIL_SIZE}" }
        require(messageId.length <= MESSAGE_ID_SIZE) { "messageId: ${messageId.length} > ${MESSAGE_ID_SIZE}" }
        require(messageFragmentNumber < messageFragmentTotal) { "messageFragmentNumber >= messageFragmentTotal: $messageFragmentNumber >= $messageFragmentTotal" }
    }
}

class StringReader(private val s: String) {
    private var position = 0

    val remaining: Int
        get() = s.length - position

    fun read(count: Int): String {
        require(count > 0) { "count must be > 0" }
        //exclusive
        val end = position + count
        if (end > s.length)
            throw IllegalStateException("EOS reached")

        val r = s.substring(position, end)
        position += count

        return r
    }
}

fun String.rstrip(): String =
    this.replaceFirst(Regex("\\s+$"), "")

fun headerFromBytes(bytes: ByteArray): Header {
    val signature = bytes.copyOfRange(0, 3)

    if (bytes.size < HEADER_SIZE)
        throw InvalidHeaderSizeException(bytes.size)

    if (!Arrays.equals(signature, SIGNATURE_BYTES))
        throw InvalidHeaderSignatureException(signature.hexify())

    val reader = StringReader(bytes.copyOfRange(3, bytes.size).toString(Charsets.US_ASCII))

    val version = reader.read(PROTOCOL_VERSION_SIZE).toInt()
    if (version != 1)
        throw InvalidProtocolVersionException(version)

    val contentLength = reader.read(CONTENT_LENGTH_SIZE).toInt()

    val fromUserToken = reader.read(FROM_USER_TOKEN_SIZE).rstrip()
    val fromUserEmail = reader.read(FROM_USER_EMAIL_SIZE).rstrip()
    val toUserEmail = reader.read(TO_USER_EMAIL_SIZE).rstrip()

    val messageId = reader.read(MESSAGE_ID_SIZE)

    val messageNumber = reader.read(MESSAGE_NUMBER_SIZE).toInt()
    val messageNumberTotal = reader.read(MESSAGE_NUMBER_TOTAL_SIZE).toInt()

    val commandCodeNumber = reader.read(COMMAND_CODE_SIZE).toInt()

    return Header(
        version,
        contentLength,
        fromUserToken,
        fromUserEmail,
        toUserEmail,
        messageId,
        messageNumber,
        messageNumberTotal,
        CommandCode.fromInt(commandCodeNumber)
    )
}

fun Int.leftZeroPad(size: Int): String =
    "%0${size}d".format(this)

fun String.rightSpacePad(size: Int): String =
    "%-${size}s".format(this)

fun headerToString(header: Header): String {
    val builder = StringBuilder()
    builder.append(SIGNATURE)
    header.apply {
        builder.append(version.leftZeroPad(PROTOCOL_VERSION_SIZE))
        builder.append(contentLength.leftZeroPad(CONTENT_LENGTH_SIZE))
        builder.append(fromUserToken.rightSpacePad(FROM_USER_TOKEN_SIZE))
        builder.append(fromUserEmail.rightSpacePad(FROM_USER_EMAIL_SIZE))
        builder.append(toUserEmail.rightSpacePad(TO_USER_EMAIL_SIZE))
        builder.append(messageId.rightSpacePad(MESSAGE_ID_SIZE))
        builder.append(messageFragmentNumber.leftZeroPad(MESSAGE_NUMBER_SIZE))
        builder.append(messageFragmentTotal.leftZeroPad(MESSAGE_NUMBER_TOTAL_SIZE))
        builder.append(commandCode.code.leftZeroPad(COMMAND_CODE_SIZE))
    }
    return builder.toString()
}

fun headerToByteArray(header: Header): ByteArray =
    headerToString(header).toByteArray(Charsets.UTF_8)

/** Create a new auth request. */
fun createAuthRequest(userCredentials: UserCredentials): RelayMessage {
    val header = Header(
        1,
        0,
        userCredentials.authToken,
        userCredentials.username,
        "",
        "",
        0,
        1,
        CommandCode.CLIENT_REGISTER_REQUEST
    )

    return RelayMessage(header, ByteArray(0))
}

fun createSendMessageMessage(userCredentials: UserCredentials, to: String, message: String, messageId: String): RelayMessage {
    val content = ByteArray(0)
    val header = Header(
        1,
        content.size,
        userCredentials.authToken,
        userCredentials.username,
        to,
        messageId,
        0,
        1,
        CommandCode.CLIENT_SEND_MESSAGE
    )

    //TODO content
    return RelayMessage(header, content)
}
