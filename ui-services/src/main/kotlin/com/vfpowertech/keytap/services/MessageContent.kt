package com.vfpowertech.keytap.services

import com.fasterxml.jackson.annotation.JsonProperty

//TODO use bytes when encrypting
/** CLIENT_SEND_MESSAGE content type. */
data class MessageContent(@JsonProperty("message") val message: String)
