@file:JvmName("RandomUtils")
package io.slychat.messenger.core

import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.crypto.ciphers.AES256GCMCipher
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.http.api.contacts.ApiContactInfo
import io.slychat.messenger.core.persistence.*
import org.whispersystems.libsignal.SignalProtocolAddress
import java.util.*

//[min, max]
fun randomInt(min: Int = 0, max: Int = Int.MAX_VALUE-1): Int =
    min + Random().nextInt((max - min) + 1)

fun randomLong(min: Int = 0, max: Int = Int.MAX_VALUE-1): Long =
    randomInt(min, max).toLong()

fun randomGroupInfo(): GroupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)
fun randomGroupInfo(membershipLevel: GroupMembershipLevel): GroupInfo =
    GroupInfo(randomGroupId(), randomGroupName(), membershipLevel)

fun randomGroupName(): String = randomUUID()

fun randomGroupMembers(n: Int = 2): Set<UserId> = (1..n).mapTo(HashSet()) { randomUserId() }

fun randomUserId(): UserId =
    UserId(randomInt(1, 10000).toLong())

fun randomSlyAddress(userId: UserId = randomUserId()): SlyAddress =
    SlyAddress(userId, randomDeviceId())

fun randomSignalAddress(userId: UserId = randomUserId()): SignalProtocolAddress =
    randomSlyAddress(userId).toSignalAddress()

fun randomDeviceId(): Int =
    randomInt(1, 50)

fun randomDeviceInfo(): DeviceInfo = DeviceInfo(randomDeviceId(), randomRegistrationId())

fun randomUserIds(n: Int = 2): Set<UserId> = (1..n).mapToSet { randomUserId() }

fun randomGroupId(): GroupId = GroupId(randomUUID())

fun randomGroupConversationId(): ConversationId.Group = ConversationId(randomGroupId())

fun randomUserConversationId(): ConversationId.User = ConversationId(randomUserId())

fun randomAuthToken(): AuthToken = AuthToken(randomUUID())

private fun <E> List<E>.randomItem(): E {
    return this[Random().nextInt(size)]
}

fun randomEmailAddress(): String =
    listOf("abcdefghijklmopqrstuvwxyz").randomItem() + "@a.com"

fun randomUserCredentials(): UserCredentials = UserCredentials(randomSlyAddress(), randomAuthToken())

fun randomTextGroupMetadata(groupId: GroupId? = null): MessageMetadata {
    return MessageMetadata(
        randomUserId(),
        groupId ?: randomGroupId(),
        MessageCategory.TEXT_GROUP,
        randomMessageId()
    )
}

fun randomTextSingleMetadata(recipientId: UserId = randomUserId()): MessageMetadata {
    val messageId = randomUUID()
    return MessageMetadata(
        recipientId,
        null,
        MessageCategory.TEXT_SINGLE,
        messageId
    )
}

fun randomOtherMetadata(recipientId: UserId = randomUserId()): MessageMetadata {
    val messageId = randomUUID()
    return MessageMetadata(
        recipientId,
        null,
        MessageCategory.OTHER,
        messageId
    )
}

fun randomSerializedMessage(): ByteArray = Random().nextInt().toString().toByteArray()

fun randomSenderMessageEntry(): SenderMessageEntry {
    return SenderMessageEntry(randomTextSingleMetadata(), randomSerializedMessage())
}

fun randomSenderMessageEntries(n: Int = 2): List<SenderMessageEntry> {
    return (1..n).map { randomSenderMessageEntry() }
}

fun randomContactInfo(allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL): ContactInfo {
    val userId = randomUserId()

    return ContactInfo(
        userId,
        "$userId@domain.com",
        userId.toString(),
        allowedMessageLevel,
        "pubkey"
    )
}

fun randomApiContactInfo(): ApiContactInfo {
    val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)

    return ApiContactInfo(
        contactInfo.id,
        contactInfo.email,
        contactInfo.name,
        null,
        contactInfo.publicKey
    )
}

fun randomContactInfoList(allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL, n: Int = 2): List<ContactInfo> {
    return (1..n).map { randomContactInfo(allowedMessageLevel) }
}

fun randomPhoneNumber(): String {
    return "1" + shuffleString("0123456789")
}

fun randomPlatformContact(): PlatformContact {
    return PlatformContact(
        randomName(),
        listOf(randomEmailAddress()),
        listOf(randomPhoneNumber())
    )
}

private fun shuffleString(input: String): String {
    val base = input.toMutableList()
    Collections.shuffle(base)

    return base.joinToString("")
}

fun randomName(): String {
    return shuffleString("random name")
}

fun randomTtl(): Long = randomInt(500, 1000).toLong()

fun randomAccountInfo(deviceId: Int = randomDeviceId()): AccountInfo {
    return AccountInfo(randomUserId(), randomName(), randomEmailAddress(), "", deviceId)
}

fun randomMessageText(): String {
    //XXX kinda dumb...
    return shuffleString("random message")
}

fun randomReceivedMessageInfo(isRead: Boolean = false, expiresAt: Long = 0): MessageInfo {
    return MessageInfo.newReceived(randomMessageText(), currentTimestamp(), isRead).copy(expiresAt = expiresAt)
}

fun randomSentMessageInfo(ttlMs: Long = 0): MessageInfo {
    return MessageInfo.newSent(randomMessageText(), ttlMs)
}

fun randomReceivedConversationMessageInfo(speaker: UserId?): ConversationMessageInfo {
    return ConversationMessageInfo(speaker, randomReceivedMessageInfo())
}

fun randomSentConversationMessageInfo(ttlMs: Long = 0): ConversationMessageInfo {
    return ConversationMessageInfo(null, randomSentMessageInfo(ttlMs))
}

fun randomLastMessageData(): LastMessageData =
    LastMessageData("speakerName", randomUserId(), randomMessageText(), currentTimestamp())

fun randomConversationDisplayInfo(): ConversationDisplayInfo =
    ConversationDisplayInfo(
        randomGroupConversationId(),
        randomGroupName(),
        1,
        randomMessageIds(1),
        randomLastMessageData()
    )

fun randomMessageIds(n: Int = 2): List<String> = (1..n).map { randomMessageId() }

fun randomSecurityEvent(target: LogTarget? = null): LogEvent.Security {
    return LogEvent.Security(
        target ?: LogTarget.Conversation(randomUserConversationId()),
        1,
        SecurityEventData.InvalidKey(randomSlyAddress(), "invalid signature on device key!")
    )
}

fun randomMessageSendFailures(userId: UserId): Map<UserId, MessageSendFailure> = mapOf(
    userId to MessageSendFailure.InactiveUser()
)

fun randomUserMetadata(): UserMetadata {
    return UserMetadata(
        Key(byteArrayOf(0x73, 0x68, 0x69, 0x6e, 0x6f, 0x7a, 0x61, 0x6b, 0x69, 0x61, 0x69)),
        "/" + randomName(),
        randomName()
    )
}

fun randomFileMetadata(): FileMetadata {
    return FileMetadata(
        randomInt().toLong(),
        AES256GCMCipher.id,
        randomInt()
    )
}

fun randomRemoteFile(isDeleted: Boolean = false): RemoteFile {
    val fileMetadata = if (!isDeleted)
        randomFileMetadata()
    else
        null

    return RemoteFile(
        generateFileId(),
        generateShareKey(),
        1,
        isDeleted,
        randomUserMetadata(),
        fileMetadata,
        1,
        2,
        randomLong()
    )
}

fun randomUpload(fileId: String? = null, fileSize: Long = 0, state: UploadState = UploadState.PENDING, error: UploadError? = null): Upload {
    val fid = fileId ?: generateFileId()

    return Upload(
        generateUploadId(),
        fid,
        state,
        "/tmp/" + randomName(),
        false,
        null,
        listOf(UploadPart(1, 0, if (fileSize == 0L) randomLong() else fileSize, false))
    )
}
