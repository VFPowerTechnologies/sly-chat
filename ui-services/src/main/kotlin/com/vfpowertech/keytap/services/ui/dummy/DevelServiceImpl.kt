package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.ui.DevelService
import com.vfpowertech.keytap.services.ui.UIContactDetails

class DummyNotAvailableException(val serviceName: String) : UnsupportedOperationException("Dummy for $serviceName is not loaded")

class DevelServiceImpl(private val messengerService: DummyMessengerService?) : DevelService {
    override fun receiveFakeMessage(contact: UIContactDetails, message: String) {
        messengerService ?: throw DummyNotAvailableException("MessengerService")
        messengerService.receiveNewMessage(contact.email, message)
    }
}