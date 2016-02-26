package com.vfpowertech.keytap.core.persistence

class InvalidConversationException(username: String) : RuntimeException("No conversation exists for the user: <$username>")