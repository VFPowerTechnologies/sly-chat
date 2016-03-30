package com.vfpowertech.keytap.core.http.api.offline

import com.fasterxml.jackson.annotation.JsonProperty

data class OfflineMessagesGetResponse(@JsonProperty("messages") val messages: List<SerializedOfflineMessage>)