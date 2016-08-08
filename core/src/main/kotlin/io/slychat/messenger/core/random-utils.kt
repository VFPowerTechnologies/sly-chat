@file:JvmName("RandomUtils")
package io.slychat.messenger.core

import io.slychat.messenger.core.persistence.*
import java.util.*

fun randomGroupInfo(): GroupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)
fun randomGroupInfo(membershipLevel: GroupMembershipLevel): GroupInfo =
    GroupInfo(randomGroupId(), randomGroupName(), membershipLevel)

fun randomGroupName(): String = randomUUID()

fun randomGroupMembers(n: Int = 2): Set<UserId> = (1..n).mapTo(HashSet()) { randomUserId() }

fun randomUserId(): UserId {
    val l = 1 + Random().nextInt(10000-1) + 1
    return UserId(l.toLong())
}

fun randomUserIds(n: Int = 2): Set<UserId> = (1..n).mapToSet { randomUserId() }

fun randomGroupId(): GroupId = GroupId(randomUUID())

fun randomMessageId(): String = randomUUID()

fun randomTextGroupMetadata(groupId: GroupId? = null): MessageMetadata {
    return MessageMetadata(
        randomUserId(),
        groupId ?: randomGroupId(),
        MessageCategory.TEXT_GROUP,
        randomMessageId()
    )
}

fun randomTextSingleMetadata(): MessageMetadata {
    val recipient = randomUserId()
    val messageId = randomUUID()
    return MessageMetadata(
        recipient,
        null,
        MessageCategory.TEXT_SINGLE,
        messageId
    )

}

fun randomSerializedMessage(): ByteArray = Random().nextInt().toString().toByteArray()

fun randomQueuedMessage(): QueuedMessage {
    val serialized = randomSerializedMessage()

    val metadata = randomTextSingleMetadata()

    val queued = QueuedMessage(
        metadata,
        currentTimestamp(),
        serialized
    )

    return queued
}

fun randomQueuedMessages(n: Int = 2): List<QueuedMessage> {
    return (1..n).map { randomQueuedMessage() }
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

fun randomMessageText(): String {
    //XXX kinda dumb...
    val base = "random message".toMutableList()
    Collections.shuffle(base)

    return base.joinToString("")
}

fun randomReceivedMessageInfo(): MessageInfo {
    return MessageInfo.newReceived(randomMessageText(), currentTimestamp())
}

fun randomReceivedMessageInfoList(n: Int = 2): List<MessageInfo> {
    return (1..n).map { MessageInfo.newReceived(randomMessageText(), currentTimestamp()) }
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
