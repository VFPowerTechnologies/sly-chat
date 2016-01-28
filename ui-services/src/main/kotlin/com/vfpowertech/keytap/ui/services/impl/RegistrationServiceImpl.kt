package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.RegistrationInfo
import com.vfpowertech.keytap.ui.services.RegistrationService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.async
import nl.komponents.kovenant.functional.map

class RegistrationServiceImpl() : RegistrationService {
    override fun doRegistration(info: RegistrationInfo, progressListener: (String) -> Unit): Promise<Unit, Exception> {
        return async() {
            //generateKeys
            progressListener("Generating keys")
            ""
        } map { keys ->
            //send data to server
            progressListener("Sending registration request to server")
        }
    }
}