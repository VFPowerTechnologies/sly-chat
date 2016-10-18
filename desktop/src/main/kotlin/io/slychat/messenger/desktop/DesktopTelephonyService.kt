package io.slychat.messenger.desktop

import io.slychat.messenger.core.UserId
import io.slychat.messenger.services.PlatformTelephonyService
import nl.komponents.kovenant.Promise

class DesktopTelephonyService : PlatformTelephonyService {
    override fun getDevicePhoneNumber(): Promise<String?, Exception> = Promise.ofSuccess(null)

    override fun supportsMakingCalls(): Boolean {
        return false
    }

    override fun callContact(userId: UserId) {
        error("Unsupported")
    }
}
