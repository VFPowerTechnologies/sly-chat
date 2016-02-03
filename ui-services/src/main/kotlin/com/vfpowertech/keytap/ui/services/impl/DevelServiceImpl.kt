package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.DevelService
import com.vfpowertech.keytap.ui.services.UIContactDetails

class DevelServiceImpl(private val messengerService: MessengerServiceImpl) : DevelService {
    override fun receiveFakeMessage(contact: UIContactDetails, message: String) {
        messengerService.receiveNewMessage(contact, message)
    }
}