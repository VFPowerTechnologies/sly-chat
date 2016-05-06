package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.UserId

class InvalidMessageException(val userId: UserId, val messageId: String) : RuntimeException("Invalid message id ($messageId) for ${userId.long}")