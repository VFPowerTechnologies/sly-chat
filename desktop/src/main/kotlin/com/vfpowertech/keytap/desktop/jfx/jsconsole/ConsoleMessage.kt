package com.vfpowertech.keytap.desktop.jfx.jsconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URL

@JsonIgnoreProperties(ignoreUnknown=true)
data class ConsoleMessage(
    @JsonProperty("source") val source: String,
    @JsonProperty("level") val level: String,
    @JsonProperty("text") val text: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("line") val line: Int,
    @JsonProperty("column") val column: Int,
    @JsonProperty("url") val url: URL,
    @JsonProperty("repeatCount") val repeatCount: Int,
    //not given for errors
    @JsonProperty("parameters") val parameters: Array<ConsoleParameter>?,
    @JsonProperty("stackTrace") val stackTrace: Array<ConsoleStackTrace>?
)