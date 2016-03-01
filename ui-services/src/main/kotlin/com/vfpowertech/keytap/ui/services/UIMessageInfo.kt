package com.vfpowertech.keytap.ui.services

/**
 * A message and its associated contact.
 *
 * @property contact Contact's email.
 */
data class UIMessageInfo(
    val contact: String,
    val message: UIMessage
)