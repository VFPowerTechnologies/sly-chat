package com.vfpowertech.keytap.core.persistence

class InvalidMessageException(val contact: String, val messageId: String) : RuntimeException("Invalid message id ($messageId) for $contact")