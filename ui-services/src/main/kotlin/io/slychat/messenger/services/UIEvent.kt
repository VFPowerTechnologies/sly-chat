package io.slychat.messenger.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes(
    JsonSubTypes.Type(PageChangeEvent::class, name = "PageChange")
)
interface UIEvent

enum class PageType { CONVO, CONTACTS, GROUP }
data class PageChangeEvent(
    @JsonProperty("page")
    val page: PageType,
    @JsonProperty("extra")
    val extra: String
) : UIEvent


