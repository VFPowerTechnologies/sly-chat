@file:JvmName("RandomUtils")
package io.slychat.messenger.core

import io.slychat.messenger.core.persistence.*
import java.util.*

fun randomGroupInfo(): GroupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)
fun randomGroupInfo(isPending: Boolean, membershipLevel: GroupMembershipLevel): GroupInfo =
    GroupInfo(randomGroupId(), randomGroupName(), isPending, membershipLevel)

fun randomGroupName(): String = randomUUID()

fun randomGroupMembers(n: Int = 2): Set<UserId> = (1..n).mapTo(HashSet()) { randomUserId() }

fun randomUserId(): UserId {
    val l = 1 + Random().nextInt(10000-1) + 1
    return UserId(l.toLong())
}

fun randomUserIds(n: Int = 2): Set<UserId> = (1..n).mapToSet { randomUserId() }

fun randomGroupId(): GroupId = GroupId(randomUUID())

fun randomMessageId(): String = randomUUID()

fun randomTextGroupMetaData(groupId: GroupId? = null): MessageMetadata {
    return MessageMetadata(
        randomUserId(),
        groupId ?: randomGroupId(),
        MessageCategory.TEXT_GROUP,
        randomMessageId()
    )
}

fun randomSerializedMessage(): ByteArray = Random().nextInt().toString().toByteArray()

fun randomQueuedMessage(): QueuedMessage {
    val recipient = randomUserId()
    val messageId = randomUUID()
    val serialized = randomSerializedMessage()

    val metadata = MessageMetadata(
        recipient,
        null,
        MessageCategory.TEXT_SINGLE,
        messageId
    )

    val queued = QueuedMessage(
        metadata,
        currentTimestamp(),
        serialized
    )

    return queued
}
