package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/** An attempt to update a non-existent contact was made */
class InvalidContactException(val userId: UserId) : RuntimeException("Invalid contact: $userId")