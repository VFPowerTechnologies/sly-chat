package com.vfpowertech.keytap.core.http.api

/** Dummy type for empty responses. */
class EmptyResponse {
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return other is EmptyResponse
    }

    override fun hashCode(): Int {
        return 0
    }
}