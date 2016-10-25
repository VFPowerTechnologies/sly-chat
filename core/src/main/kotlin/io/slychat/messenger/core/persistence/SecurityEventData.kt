package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.slychat.messenger.core.SlyAddress

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(SecurityEventData.InvalidKey::class),
    JsonSubTypes.Type(SecurityEventData.InvalidMessage::class),
    JsonSubTypes.Type(SecurityEventData.NoSession::class),
    JsonSubTypes.Type(SecurityEventData.InvalidPreKeyId::class),
    JsonSubTypes.Type(SecurityEventData.InvalidSignedPreKeyId::class),
    JsonSubTypes.Type(SecurityEventData.UntrustedIdentity::class),
    JsonSubTypes.Type(SecurityEventData.DuplicateMessage::class),
    JsonSubTypes.Type(SecurityEventData.SessionCreated::class),
    JsonSubTypes.Type(SecurityEventData.SessionRemoved::class)
)
sealed class SecurityEventData : EventData {
    class InvalidKey(
        @JsonProperty("sender")
        val sender: SlyAddress,
        @JsonProperty("issue")
        val issue: String
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as InvalidKey

            if (sender != other.sender) return false
            if (issue != other.issue) return false

            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + issue.hashCode()
            return result
        }

        override fun toString(): String {
            return "InvalidKey(sender=$sender, issue='$issue')"
        }

        override fun toDisplayString(): String {
            return "An invalid key was received from ${sender.asString()}: $issue"
        }
    }

    class InvalidMessage(
        @JsonProperty("sender")
        val sender: SlyAddress,
        @JsonProperty("issue")
        val issue: String
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as InvalidMessage

            if (sender != other.sender) return false
            if (issue != other.issue) return false

            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + issue.hashCode()
            return result
        }

        override fun toString(): String {
            return "InvalidMessage(sender=$sender, issue='$issue')"
        }

        override fun toDisplayString(): String {
            return "An invalid message was received from ${sender.asString()}: $issue"
        }
    }

    class NoSession(
        @JsonProperty("sender")
        val sender: SlyAddress
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as NoSession

            if (sender != other.sender) return false

            return true
        }

        override fun hashCode(): Int {
            return sender.hashCode()
        }

        override fun toString(): String {
            return "NoSession(sender=$sender)"
        }

        override fun toDisplayString(): String {
            return "No session found for ${sender.asString()}"
        }
    }

    class InvalidPreKeyId(
        @JsonProperty("sender")
        val sender: SlyAddress,
        @JsonProperty("id")
        val id: Int
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as InvalidPreKeyId

            if (sender != other.sender) return false
            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + id
            return result
        }

        override fun toString(): String {
            return "InvalidPreKeyId(sender=$sender, id=$id)"
        }

        override fun toDisplayString(): String {
            return "Invalid PreKey ID from ${sender.asString()}: $id"
        }
    }

    class InvalidSignedPreKeyId(
        @JsonProperty("sender")
        val sender: SlyAddress,
        @JsonProperty("id")
        val id: Int
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as InvalidSignedPreKeyId

            if (sender != other.sender) return false
            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + id
            return result
        }

        override fun toString(): String {
            return "InvalidSignedPreKeyId(sender=$sender, id=$id)"
        }

        override fun toDisplayString(): String {
            return "Invalid signed PreKey ID from ${sender.asString()}: $id"
        }
    }

    class UntrustedIdentity(
        @JsonProperty("sender")
        val sender: SlyAddress,
        @JsonProperty("receivedIdentityKey")
        val receivedIdentityKey: String
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as UntrustedIdentity

            if (sender != other.sender) return false
            if (receivedIdentityKey != other.receivedIdentityKey) return false

            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + receivedIdentityKey.hashCode()
            return result
        }

        override fun toString(): String {
            return "UntrustedIdentity(sender=$sender, receivedIdentityKey='$receivedIdentityKey')"
        }

        override fun toDisplayString(): String {
            return "Received message from ${sender.asString()}, but $receivedIdentityKey doesn't match recorded identity key"
        }
    }

    class DuplicateMessage(
        @JsonProperty("sender")
        val sender: SlyAddress
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as DuplicateMessage

            if (sender != other.sender) return false

            return true
        }

        override fun hashCode(): Int {
            return sender.hashCode()
        }

        override fun toString(): String {
            return "DuplicateMessage(sender=$sender)"
        }

        override fun toDisplayString(): String {
            return "Duplicate message from ${sender.asString()}"
        }
    }

    class SessionCreated(
        @JsonProperty("address")
        val address: SlyAddress,
        @JsonProperty("remoteRegistrationId")
        val remoteRegistrationId: Int
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as SessionCreated

            if (address != other.address) return false
            if (remoteRegistrationId != other.remoteRegistrationId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = address.hashCode()
            result = 31 * result + remoteRegistrationId
            return result
        }

        override fun toString(): String {
            return "SessionCreated(address=$address, remoteRegistrationId=$remoteRegistrationId)"
        }

        override fun toDisplayString(): String {
            return "Created new signal session for ${address.asString()}, registrationId=$remoteRegistrationId"
        }
    }

    class SessionRemoved(
        @JsonProperty("address")
        val address: SlyAddress,
        @JsonProperty("remoteRegistrationId")
        val remoteRegistrationId: Int
    ) : SecurityEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as SessionRemoved

            if (address != other.address) return false
            if (remoteRegistrationId != other.remoteRegistrationId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = address.hashCode()
            result = 31 * result + remoteRegistrationId
            return result
        }

        override fun toString(): String {
            return "SessionRemoved(address=$address, remoteRegistrationId=$remoteRegistrationId)"
        }

        override fun toDisplayString(): String {
            return "Removed signal session for ${address.asString()}, registrationId=$remoteRegistrationId"
        }
    }
}