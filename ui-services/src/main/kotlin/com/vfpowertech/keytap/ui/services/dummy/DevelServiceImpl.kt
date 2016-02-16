package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.DevelService
import com.vfpowertech.keytap.ui.services.UIContactDetails

class DummyNotAvailable(val serviceName: String) : IllegalStateException("Dummy for $serviceName is not loaded")

class DevelServiceImpl(private val messengerService: DummyMessengerService?) : DevelService {
    override fun receiveFakeMessage(contact: UIContactDetails, message: String) {
        messengerService ?: throw DummyNotAvailable("MessengerService")
        messengerService.receiveNewMessage(contact, message)
    }
}