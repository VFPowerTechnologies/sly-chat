@file:JvmName("ImplUtils")
package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.UserConversation
import io.slychat.messenger.services.ui.UIContactInfo
import io.slychat.messenger.services.ui.UIConversation
import io.slychat.messenger.services.ui.UIConversationInfo
import io.slychat.messenger.services.ui.UIMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

fun asyncGenerateNewKeyVault(password: String): Promise<KeyVault, Exception> = task {
    generateNewKeyVault(password)
}

fun MessageInfo.toUI(): UIMessage {
    return UIMessage(id, isSent, timestamp, receivedTimestamp, message, ttlMs, expiresAt, isExpired)
}

fun ContactInfo.toUI(): UIContactInfo =
    UIContactInfo(id, name, phoneNumber, email, publicKey, allowedMessageLevel)

fun Iterable<ContactInfo>.toUI(): List<UIContactInfo> =
    map { it.toUI() }

fun UIContactInfo.toNative(): ContactInfo =
    ContactInfo(id, email, name, allowedMessageLevel, phoneNumber, publicKey)

fun UserConversation.toUI(): UIConversation {
    return UIConversation(contact.toUI(), UIConversationInfo(true, info.unreadMessageCount, info.lastMessage, info.lastTimestamp))
}
