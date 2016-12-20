package io.slychat.messenger.services.ui.js

import com.vfpowertech.jsbridge.processor.annotations.JSServiceName
import com.vfpowertech.jsbridge.processor.annotations.JavaToJSGenerate
import io.slychat.messenger.core.persistence.ConversationId
import nl.komponents.kovenant.Promise

/** Native OS navigation support. */
@JavaToJSGenerate
@JSServiceName("navigationService")
interface NavigationService {
    /** Perform a back action in the UI. */
    fun goBack(): Promise<Unit, Exception>

    /** Go to the specified url. */
    fun goTo(url: String): Promise<Unit, Exception>
}

/** Returns the URL for a particular conversation. */
fun getNavigationPageConversation(conversationId: ConversationId): String {
    return when (conversationId) {
        is ConversationId.User -> "user/${conversationId.id}"
        is ConversationId.Group -> "group/${conversationId.id}"
    }
}

/** Returns the URL for the contacts page. */
fun getNavigationPageContacts(): String = "contacts"
