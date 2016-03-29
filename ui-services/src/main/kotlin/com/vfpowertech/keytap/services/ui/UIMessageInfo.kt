package com.vfpowertech.keytap.services.ui

/**
 * A message and its associated contact.
 *
 * @property contact Contact's email.
 */
data class UIMessageInfo(
    val contact: String,
    val messages: List<UIMessage>
)