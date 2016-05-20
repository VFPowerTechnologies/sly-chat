package io.slychat.messenger.desktop.jfx.jsconsole

import com.fasterxml.jackson.annotation.JsonProperty

data class ConsoleMessageAdded(
    @JsonProperty("message") val message: ConsoleMessage
)