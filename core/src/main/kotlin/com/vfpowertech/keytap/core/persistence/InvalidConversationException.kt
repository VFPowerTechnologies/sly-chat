package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.UserId

class InvalidConversationException(val userId: UserId) : RuntimeException("No conversation exists for the user id: ${userId.long}")