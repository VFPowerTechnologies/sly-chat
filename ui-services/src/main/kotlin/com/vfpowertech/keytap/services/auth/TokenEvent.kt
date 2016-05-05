package com.vfpowertech.keytap.services.auth

sealed class TokenEvent {
    class Expired() : TokenEvent() {
        override fun toString(): String =
            "Expired()"
    }

    //may or not may be preceeded by an Expired event; could be refreshed in the background without expiring
    class New(val authToken: AuthToken) : TokenEvent() {
        //purposely hide the auth token; don't want it accidently showing up in logs/etc
        override fun toString(): String = "New()"
    }

    class Error(val cause: Exception) : TokenEvent() {
        override fun toString(): String = "Error(${cause.message})"
    }
}