package io.slychat.messenger.desktop.jfx.jsconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConsoleParameter(
    @JsonProperty("type") val type: String,
    @JsonProperty("objectId") val objectId: String?,
    //this is optional if type == object
    //{"type":"object","objectId":"{\"injectedScriptId\":1,\"id\":1}","className":"Promise","description":"Promise","preview":{"lossless":true,"overflow":false,"properties":[]}}],
    //{"method":"Console.messageAdded","params":{"message":{"source":"console-api","level":"log","text":"0","type":"log","line":59,"column":16,"url":"file:///home/kevin/code/prototypes/js-bridge/build/resources/main/script.js","repeatCount":1,"parameters":[{"type":"object","objectId":"{\"injectedScriptId\":1,\"id\":2}","subtype":"array","className":"Array","description":"Array[1]","preview":{"lossless":true,"overflow":false,"properties":[{"name":"0","type":"number","value":"0"}]}}],"stackTrace":[{"functionName":"callFromNative","url":"file:///home/kevin/code/prototypes/js-bridge/build/resources/main/script.js","lineNumber":59,"columnNumber":16},{"functionName":"global code","url":"","lineNumber":1,"columnNumber":33}]}}}
    @JsonProperty("subtype") val subtype: String?,
    @JsonProperty("className") val className: String?,
    @JsonProperty("description") val description: String?,
    //is actually an obj
    //@JsonProperty("preview") val preview: String?,
    @JsonProperty("value") val value: Any?
)