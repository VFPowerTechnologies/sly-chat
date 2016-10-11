package io.slychat.messenger.core.persistence

/** Raised when attempting operations on groups that don't exist. */
class InvalidGroupException(val groupId: GroupId) : RuntimeException("Invalid group: $groupId")