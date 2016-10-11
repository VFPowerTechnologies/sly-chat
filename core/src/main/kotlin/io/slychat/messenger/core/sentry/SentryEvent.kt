package io.slychat.messenger.core.sentry

import com.fasterxml.jackson.annotation.JsonProperty

import io.slychat.messenger.core.BuildConfig

//https://docs.getsentry.com/hosted/clientdev/ for general client info
//https://docs.getsentry.com/hosted/clientdev/interfaces/ for interface documentation

data class MessageInterface(
    val message: String,
    val params: Collection<String>
)

data class SentryStackFrame(
    val filename: String,
    val module: String,
    //note that at least the top most non-app frame will still be shown, but nothing below it
    @get:JsonProperty("in_app")
    val inApp: Boolean,
    val function: String,
    val lineno: Int
)

data class StacktraceInterface(
    val frames: Collection<SentryStackFrame>
)

data class ExceptionInterface(
    //IllegalArgumentException
    val type: String,
    //.message
    val value: String?,
    //java.lang (pkg of type)
    val module: String?,
    val stacktrace: StacktraceInterface?
)

data class UserInterface(
    val id: String,
    val username: String
)

data class SentryEvent(
    @get:JsonProperty("event_id")
    val eventId: String,
    val logger: String,
    val level: String,
    val message: String,
    //this is set to either the caller data's top stack element, or the logger name if that's not available
    //this only shows up in Sentry if the exception interface is present
    val culprit: String,
    val timestamp: String,
    @get:JsonProperty("sentry.interfaces.Message")
    val messageInterface: MessageInterface?,
    //non-empty list or null
    @get:JsonProperty("sentry.interfaces.Exception")
    val exception: Collection<ExceptionInterface>?,
    @get:JsonProperty("sentry.interfaces.User")
    val user: UserInterface?,
    val tags: Map<String, String>,
    //thread-name etc
    val extra: Map<String, String>
) {
    val platform = "java"

    val release: String? = BuildConfig.VERSION
}