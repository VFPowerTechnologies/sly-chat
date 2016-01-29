package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.MessengerService
import com.vfpowertech.keytap.ui.services.UIMessage
import nl.komponents.kovenant.Promise
import java.util.*

class MessengerServiceImpl : MessengerService {
    private val newMessageListeners = ArrayList<(UIMessage) -> Unit>()

    override fun sendMessageTo(contactName: String, message: String): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }

    override fun addNewMessageListener(listener: (UIMessage) -> Unit) {
        synchronized(this) {
            newMessageListeners.add(listener)
        }
    }

    override fun getLastMessagesFor(contactName: String, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception> {
        val messages = ArrayList<UIMessage>()
        for (i in 0..count-1) {
            //TODO will be formatted according to the user's locale settings
            messages.add(UIMessage(contactName, "YYYY-MM-DD HH:MM:SS", "Message $i"))
        }
        return Promise.ofSuccess(messages)
    }
}