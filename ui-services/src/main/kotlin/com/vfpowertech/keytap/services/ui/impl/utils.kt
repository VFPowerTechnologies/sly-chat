@file:JvmName("ImplUtils")
package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.services.ui.UIContactDetails
import com.vfpowertech.keytap.services.ui.UIMessage
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

