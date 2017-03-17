package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.MessageSendFailure

class AndroidUIMessageInfo(messageInfo: ConversationMessageInfo) {
    val speakerId: UserId?
    val messageId: String
    val message: String
    val timestamp: Long
    var receivedTimestamp: Long
    val isSent: Boolean
    val isDelivered: Boolean
    val isRead: Boolean
    var isExpired: Boolean
    var ttl: Long
    var expiresAt: Long
    var failures: Map<UserId, MessageSendFailure>

    init {
        speakerId = messageInfo.speaker
        messageId = messageInfo.info.id
        message = messageInfo.info.message
        timestamp = messageInfo.info.timestamp
        receivedTimestamp = messageInfo.info.receivedTimestamp
        isSent = messageInfo.info.isSent
        isDelivered = messageInfo.info.isDelivered
        isRead = messageInfo.info.isRead
        isExpired = messageInfo.info.isExpired
        ttl = messageInfo.info.ttlMs
        expiresAt = messageInfo.info.expiresAt
        failures = messageInfo.failures
    }

    fun startExpiration(ttlMs: Long, expireTime: Long) {
        ttl = ttlMs
        expiresAt = expireTime
    }

    fun toConversationMessageInfo(): ConversationMessageInfo {
        return ConversationMessageInfo(
                speakerId,
                MessageInfo(
                        messageId,
                        message,
                        timestamp,
                        receivedTimestamp,
                        isSent,
                        isDelivered,
                        isRead,
                        isExpired,
                        ttl,
                        expiresAt
                ),
                failures
        )
    }
}