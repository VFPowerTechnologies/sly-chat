package io.slychat.messenger.core.persistence

sealed class ContactDiffDelta {
    class Added(val contactInfo: ContactInfo) : ContactDiffDelta() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Added

            if (contactInfo != other.contactInfo) return false

            return true
        }

        override fun hashCode(): Int {
            return contactInfo.hashCode()
        }

        override fun toString(): String {
            return "Add(contactInfo=$contactInfo)"
        }
    }

    class Updated(val old: ContactInfo, val new: ContactInfo) : ContactDiffDelta() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Updated

            if (old != other.old) return false
            if (new != other.new) return false

            return true
        }

        override fun hashCode(): Int {
            var result = old.hashCode()
            result = 31 * result + new.hashCode()
            return result
        }

        override fun toString(): String {
            return "Updated(old=$old, new=$new)"
        }
    }
}