@file:JvmName("ImplUtils")
package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.services.ui.UIContactDetails
import io.slychat.messenger.services.ui.UIMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

fun asyncGenerateNewKeyVault(password: String): Promise<KeyVault, Exception> = task {
    generateNewKeyVault(password)
}

fun MessageInfo.toUI(): UIMessage {
    val timestamp = if (!isDelivered) null else timestamp
    return UIMessage(id, isSent, timestamp, message)
}

fun ContactInfo.toUI(): UIContactDetails =
    UIContactDetails(id, name, phoneNumber, email, publicKey)

fun UIContactDetails.toNative(): ContactInfo =
    ContactInfo(id, email, name, phoneNumber, publicKey)

