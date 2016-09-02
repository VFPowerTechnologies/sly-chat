@file:JvmName("RandomUtils")
package io.slychat.messenger.core

import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.crypto.randomRegistrationId
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.*
import org.whispersystems.libsignal.SignalProtocolAddress
import java.util.*

//[min, max]
fun randomInt(min: Int = 0, max: Int = Int.MAX_VALUE-1): Int =
    min + Random().nextInt((max - min) + 1)

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
        null,
        "pubkey"
    )
}

fun randomContactInfoList(allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL, n: Int = 2): List<ContactInfo> {
    return (1..n).map { randomContactInfo(allowedMessageLevel) }
}

private fun shuffleString(input: String): String {
    val base = input.toMutableList()
    Collections.shuffle(base)

    return base.joinToString("")
}

fun randomName(): String {
    return shuffleString("random name")
}

fun randomAccountInfo(deviceId: Int = randomDeviceId()): AccountInfo {
    return AccountInfo(randomUserId(), randomName(), randomEmailAddress(), "", deviceId)
}

fun randomMessageText(): String {
    //XXX kinda dumb...
    return shuffleString("random message")
}

fun randomReceivedMessageInfo(): MessageInfo {
    return MessageInfo.newReceived(randomMessageText(), currentTimestamp())
}

fun randomSentMessageInfo(): MessageInfo {
    return MessageInfo.newSent(randomMessageText(), 0)
}

fun randomReceivedGroupMessageInfo(speaker: UserId?): GroupMessageInfo {
    return GroupMessageInfo(speaker, randomReceivedMessageInfo())
}

fun randomSentGroupMessageInfo(): GroupMessageInfo {
    return GroupMessageInfo(null, randomSentMessageInfo())
}
