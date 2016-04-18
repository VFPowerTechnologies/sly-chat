package com.vfpowertech.keytap.core.relay

import com.vfpowertech.keytap.core.UserId

/** Relay server user credentials. */
data class UserCredentials(val userId: UserId, val username: String, val authToken: String)