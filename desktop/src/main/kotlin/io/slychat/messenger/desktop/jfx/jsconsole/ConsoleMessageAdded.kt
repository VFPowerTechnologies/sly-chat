package io.slychat.messenger.desktop.jfx.jsconsole

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.desktop.jfx.jsconsole.ConsoleMessage

data class ConsoleMessageAdded(
    @JsonProperty("message") val message: ConsoleMessage
)