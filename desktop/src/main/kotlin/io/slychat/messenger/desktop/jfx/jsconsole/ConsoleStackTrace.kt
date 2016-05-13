package io.slychat.messenger.desktop.jfx.jsconsole

import com.fasterxml.jackson.annotation.JsonProperty

data class ConsoleStackTrace(
    @JsonProperty("functionName") val functionName: String,
    //this can be omitted if using calling into js from java
    //this can be "", which URL() blocks up with
    @JsonProperty("url") val url: String,
    @JsonProperty("lineNumber") val lineNumber: Int,
    @JsonProperty("columnNumber") val columnNumber: Int
)