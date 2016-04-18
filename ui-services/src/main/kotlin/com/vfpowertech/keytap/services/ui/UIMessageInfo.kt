package com.vfpowertech.keytap.services.ui

import com.vfpowertech.keytap.core.UserId

/**
 * A message and its associated contact.
 *
 * @property contactId Contact's email.
 */
data class UIMessageInfo(
    val contactId: UserId,
    val messages: List<UIMessage>
)