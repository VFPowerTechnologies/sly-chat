package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.UserId

class InvalidMessageLevelException(val userId: UserId) : Exception("User does not have ALL allowed message level")