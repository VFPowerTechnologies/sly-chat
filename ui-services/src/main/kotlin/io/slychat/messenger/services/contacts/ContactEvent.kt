package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo

interface ContactEvent {
    class Added(val contacts: List<ContactInfo>, val fromSync: Boolean) : ContactEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Added

            if (contacts != other.contacts) return false
            if (fromSync != other.fromSync) return false

            return true
        }

        override fun hashCode(): Int {
            var result = contacts.hashCode()
            result = 31 * result + fromSync.hashCode()
            return result
        }

        override fun toString(): String {
            return "Added(contacts=$contacts, fromSync=$fromSync)"
        }
    }

    class Removed(val contacts: List<ContactInfo>) : ContactEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Removed

            if (contacts != other.contacts) return false

            return true
        }

        override fun hashCode(): Int {
            return contacts.hashCode()
        }

        override fun toString(): String {
            return "Removed(contacts=$contacts)"
        }
    }

    class Updated(val contacts: List<ContactUpdate>, val fromSync: Boolean) : ContactEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Updated

            if (contacts != other.contacts) return false
            if (fromSync != other.fromSync) return false

            return true
        }

        override fun hashCode(): Int {
            var result = contacts.hashCode()
            result = 31 * result + fromSync.hashCode()
            return result
        }

        override fun toString(): String {
            return "Updated(contacts=$contacts, fromSync=$fromSync)"
        }
    }

    class Sync(val isRunning: Boolean) : ContactEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Sync

            if (isRunning != other.isRunning) return false

            return true
        }

        override fun hashCode(): Int {
            return isRunning.hashCode()
        }

        override fun toString(): String {
            return "Sync(isRunning=$isRunning)"
        }
    }

    class Blocked(val userId: UserId) : ContactEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Blocked

            if (userId != other.userId) return false

            return true
        }

        override fun hashCode(): Int {
            return userId.hashCode()
        }

        override fun toString(): String {
            return "Blocked(userId=$userId)"
        }
    }

    class Unblocked(val userId: UserId) : ContactEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Unblocked

            if (userId != other.userId) return false

            return true
        }

        override fun hashCode(): Int {
            return userId.hashCode()
        }

        override fun toString(): String {
            return "Unblocked(userId=$userId)"
        }
    }
}
