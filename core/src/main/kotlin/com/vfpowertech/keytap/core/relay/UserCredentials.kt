package com.vfpowertech.keytap.core.relay

import com.vfpowertech.keytap.core.KeyTapAddress

/** Relay server user credentials. */
data class UserCredentials(val address: KeyTapAddress, val authToken: String)
