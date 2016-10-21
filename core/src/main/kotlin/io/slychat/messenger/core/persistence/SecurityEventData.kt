package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(SecurityEventData.InvalidKey::class),
    JsonSubTypes.Type(SecurityEventData.InvalidMessage::class),
    JsonSubTypes.Type(SecurityEventData.NoSession::class),
    JsonSubTypes.Type(SecurityEventData.InvalidPreKeyId::class),
    JsonSubTypes.Type(SecurityEventData.InvalidSignedPreKeyId::class),
    JsonSubTypes.Type(SecurityEventData.UntrustedIdentity::class),
    JsonSubTypes.Type(SecurityEventData.SessionCreated::class),
    JsonSubTypes.Type(SecurityEventData.SessionRemoved::class)
)
sealed class SecurityEventData {
    class InvalidKey() : SecurityEventData() {
        override fun hashCode(): Int {
            return 0
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other?.javaClass == javaClass
        }

        override fun toString(): String {
            return "InvalidKey()"
        }
    }

    class InvalidMessage() : SecurityEventData() {
        override fun hashCode(): Int {
            return 0
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other?.javaClass == javaClass
        }

        override fun toString(): String {
            return "InvalidMessage()"
        }
    }

    class NoSession() : SecurityEventData() {
        override fun hashCode(): Int {
            return 0
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other?.javaClass == javaClass
        }

        override fun toString(): String {
            return "NoSession()"
        }
    }

    class InvalidPreKeyId(val deviceId: Int, val id: Int) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as InvalidPreKeyId

            if (deviceId != other.deviceId) return false
            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deviceId
            result = 31 * result + id
            return result
        }

        override fun toString(): String {
            return "InvalidPreKeyId(deviceId=$deviceId, id=$id)"
        }
    }

    class InvalidSignedPreKeyId(val deviceId: Int, val id: Int) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as InvalidSignedPreKeyId

            if (deviceId != other.deviceId) return false
            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deviceId
            result = 31 * result + id
            return result
        }

        override fun toString(): String {
            return "InvalidSignedPreKeyId(deviceId=$deviceId, id=$id)"
        }
    }

    class UntrustedIdentity(val identityKey: String, val receivedIdentityKey: String) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as UntrustedIdentity

            if (identityKey != other.identityKey) return false
            if (receivedIdentityKey != other.receivedIdentityKey) return false

            return true
        }

        override fun hashCode(): Int {
            var result = identityKey.hashCode()
            result = 31 * result + receivedIdentityKey.hashCode()
            return result
        }

        override fun toString(): String {
            return "UntrustedIdentity(identityKey='$identityKey', receivedIdentityKey='$receivedIdentityKey')"
        }
    }

    class SessionCreated(val deviceId: Int, val remoteRegistrationId: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as SessionCreated

            if (deviceId != other.deviceId) return false
            if (remoteRegistrationId != other.remoteRegistrationId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deviceId
            result = 31 * result + remoteRegistrationId
            return result
        }

        override fun toString(): String {
            return "SessionCreated(deviceId=$deviceId, remoteRegistrationId=$remoteRegistrationId)"
        }
    }

    class SessionRemoved(val deviceId: Int, val remoteRegistrationId: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as SessionRemoved

            if (deviceId != other.deviceId) return false
            if (remoteRegistrationId != other.remoteRegistrationId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deviceId
            result = 31 * result + remoteRegistrationId
            return result
        }

        override fun toString(): String {
            return "SessionRemoved(deviceId=$deviceId, remoteRegistrationId=$remoteRegistrationId)"
        }
    }
}