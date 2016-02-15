package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.UIRegistrationInfo
import com.vfpowertech.keytap.ui.services.UIRegistrationResult
import com.vfpowertech.keytap.ui.services.RegistrationService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import java.util.*

class DummyRegistrationService() : RegistrationService {
    private val listeners = ArrayList<(String) -> Unit>()

    override fun addListener(listener: (String) -> Unit) {
        synchronized(this) {
            listeners.add(listener)
        }
    }

    private fun updateProgress(status: String) {
        synchronized(this) {
            for (listener in listeners)
                listener(status)
        }
    }

    override fun doRegistration(info: UIRegistrationInfo): Promise<UIRegistrationResult, Exception> {
        return task {
            updateProgress("Generating keys")
        } map { keys ->
            updateProgress("Sending registration request to server")
        } map {
            updateProgress("Updating prekeys")
            UIRegistrationResult(true, null, null)
        }
    }
}