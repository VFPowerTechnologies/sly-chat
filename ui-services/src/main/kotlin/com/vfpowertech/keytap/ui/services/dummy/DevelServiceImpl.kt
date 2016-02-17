package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.DevelService
import com.vfpowertech.keytap.ui.services.UIContactDetails

class DummyNotAvailableException(val serviceName: String) : UnsupportedOperationException("Dummy for $serviceName is not loaded")

class DevelServiceImpl(private val messengerService: DummyMessengerService?) : DevelService {
    override fun receiveFakeMessage(contact: UIContactDetails, message: String) {
        messengerService ?: throw DummyNotAvailableException("MessengerService")
        messengerService.receiveNewMessage(contact, message)
    }
}