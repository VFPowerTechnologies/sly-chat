package io.slychat.messenger.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes(
    JsonSubTypes.Type(UIEvent.PageChange::class, name = "PageChange")
)
sealed class UIEvent {
    class PageChange(
        @JsonProperty("page")
        val page: PageType,
        @JsonProperty("extra")
        val extra: String
    ) : UIEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as PageChange

            if (page != other.page) return false
            if (extra != other.extra) return false

            return true
        }

        override fun hashCode(): Int {
            var result = page.hashCode()
            result = 31 * result + extra.hashCode()
            return result
        }

        override fun toString(): String {
            return "PageChange(page=$page, extra='$extra')"
        }

    }
}

enum class PageType { CONVO, CONTACTS, GROUP, ADDRESS_BOOK }
