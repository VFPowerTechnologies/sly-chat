package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.ui.UIRegistrationInfo
import com.vfpowertech.keytap.services.ui.UIRegistrationService
import com.vfpowertech.keytap.services.ui.UIRegistrationResult
import com.vfpowertech.keytap.services.ui.UISmsVerificationStatus
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import java.util.ArrayList

class DummyUIRegistrationService() : UIRegistrationService {
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

    override fun submitVerificationCode(info: UIRegistrationInfo, code: String): Promise<UISmsVerificationStatus, Exception> {
        return Promise.ofSuccess(UISmsVerificationStatus(true, null));
    }

    override fun resendVerificationCode(info: UIRegistrationInfo): Promise<UISmsVerificationStatus, Exception> {
        return Promise.ofSuccess(UISmsVerificationStatus(true, null));
    }
}