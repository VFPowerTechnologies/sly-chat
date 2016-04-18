package com.vfpowertech.keytap.services.ui

import com.vfpowertech.keytap.core.UserId

/**
 * A message and its associated contact.
 *
 * @property contact Contact's user id.
 */
data class UIMessageInfo(
    val contact: UserId,
    val messages: List<UIMessage>
)