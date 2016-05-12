package io.slychat.messenger.services.ui.dummy

import io.slychat.messenger.services.ui.UIDevelService
import io.slychat.messenger.services.ui.UIContactDetails

class DummyNotAvailableException(val serviceName: String) : UnsupportedOperationException("Dummy for $serviceName is not loaded")

class UIDevelServiceImpl(private val messengerService: DummyUIMessengerService?) : UIDevelService {
    override fun receiveFakeMessage(contact: UIContactDetails, message: String) {
        messengerService ?: throw DummyNotAvailableException("MessengerService")
        messengerService.receiveNewMessage(contact.id, message)
    }
}