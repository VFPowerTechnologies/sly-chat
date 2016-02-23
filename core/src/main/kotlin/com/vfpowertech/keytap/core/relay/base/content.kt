/** Relay message content-related functions. */
@file:JvmName("RelayContent")
package com.vfpowertech.keytap.core.relay.base

import com.fasterxml.jackson.annotation.JsonProperty

//TODO use bytes when encrypting
/** CLIENT_SEND_MESSAGE content type. */
data class MessageContent(@JsonProperty("message") val message: String)