@file:JvmName("ImplUtils")
package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.http.api.contacts.ApiContactInfo
import io.slychat.messenger.core.persistence.AllowedMessageLevel
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
    return UIMessage(id, isSent, timestamp, receivedTimestamp, message)
}

fun ApiContactInfo.toUI(): UIContactDetails =
    UIContactDetails(id, name, phoneNumber, email, publicKey)

fun ContactInfo.toUI(): UIContactDetails =
    UIContactDetails(id, name, phoneNumber, email, publicKey)

fun Iterable<ContactInfo>.toUI(): List<UIContactDetails> =
    map { it.toUI() }

//FIXME
fun UIContactDetails.toNative(): ContactInfo =
    ContactInfo(id, email, name, AllowedMessageLevel.ALL, false, phoneNumber, publicKey)

