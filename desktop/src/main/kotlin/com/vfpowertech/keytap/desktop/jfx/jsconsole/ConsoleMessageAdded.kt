package com.vfpowertech.keytap.desktop.jfx.jsconsole

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.desktop.jfx.jsconsole.ConsoleMessage

data class ConsoleMessageAdded(
    @JsonProperty("message") val message: ConsoleMessage
)