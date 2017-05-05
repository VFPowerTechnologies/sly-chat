@file:JvmName("RandomUtils")
package io.slychat.messenger.core

import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.files.FileMetadata
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.SharedFrom
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

fun randomMessageAttachmentInfo(n: Int): MessageAttachmentInfo {
    return MessageAttachmentInfo(
        n,
        shuffleString("dummy.jpg"),
        generateFileId(),
        true
    )
}

fun randomSharedFrom(): SharedFrom = SharedFrom(randomUserId(), randomGroupId())

fun randomReceivedAttachment(
    n: Int = 0,
    fileId: String? = null,
    error: AttachmentError? = null,
    conversationId: ConversationId? = null,
    messageId: String? = null,
    state: ReceivedAttachmentState = ReceivedAttachmentState.PENDING
): ReceivedAttachment {
    return ReceivedAttachment(
        AttachmentId(
            conversationId ?: randomUserConversationId(),
            messageId ?: randomMessageId(),
            n
        ),
        fileId ?: generateFileId(),
        generateShareKey(),
        randomUserMetadata(sharedFrom = randomSharedFrom()),
        state,
        error
    )
}

fun randomReceivedMessageInfo(isRead: Boolean = false, expiresAt: Long = 0, attachments: List<MessageAttachmentInfo> = emptyList()): MessageInfo {
    return MessageInfo.newReceived(randomMessageText(), currentTimestamp(), isRead, attachments).copy(expiresAt = expiresAt)
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

fun randomUserMetadata(directory: String? = null, fileName: String? = null, sharedFrom: SharedFrom? = null): UserMetadata {
    return UserMetadata(
        Key(byteArrayOf(0x73, 0x68, 0x69, 0x6e, 0x6f, 0x7a, 0x61, 0x6b, 0x69, 0x61, 0x69)),
        CipherList.defaultDataEncryptionCipher.id,
        directory ?: "/" + randomName(),
        fileName ?: randomName(),
        sharedFrom
    )
}

fun randomFileMetadata(fileSize: Long? = null, mimeType: String? = null): FileMetadata {
    return FileMetadata(
        fileSize ?: randomInt().toLong(),
        randomInt(),
        mimeType ?: "*/*"
    )
}

fun randomRemoteFile(fileId: String? = null, isDeleted: Boolean = false, userMetadata: UserMetadata? = null, fileMetadata: FileMetadata? = null): RemoteFile {
    val fm = fileMetadata ?: if (!isDeleted) randomFileMetadata() else null

    return RemoteFile(
        fileId ?: generateFileId(),
        generateShareKey(),
        1,
        isDeleted,
        userMetadata ?: randomUserMetadata(),
        fm,
        1,
        2,
        randomLong()
    )
}

fun randomUpload(fileId: String? = null, fileSize: Long = 0, state: UploadState = UploadState.PENDING, error: UploadError? = null): Upload {
    val localSize = if (fileSize == 0L) randomLong() else fileSize
    val remoteSize = localSize + 1
    val fileName = randomName()

    return Upload(
        generateUploadId(),
        fileId ?: generateFileId(),
        state,
        fileName,
        "/remote/" + fileName,
        "/local/" + fileName,
        null,
        false,
        error,
        listOf(UploadPart(1, 0, localSize, remoteSize, false))
    )
}

fun randomDownload(fileId: String? = null, state: DownloadState = DownloadState.CREATED, error: DownloadError? = null): Download {
    return Download(
        generateDownloadId(),
        fileId ?: generateFileId(),
        state,
        "/local/" + randomName(),
        "/remote/" + randomName(),
        false,
        error
    )
}

fun randomQuota(): Quota {
    val max = randomInt()
    val used = randomInt(max = max)

    return Quota(used.toLong(), max.toLong())
}

fun randomPathHash(): String {
    val random = Random()
    val hash = ByteArray(16)
    random.nextBytes(hash)
    return hash.hexify()
}
