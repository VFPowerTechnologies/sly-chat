package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.UserId
import io.slychat.messenger.services.PlatformTelephonyService
import io.slychat.messenger.services.ui.UITelephonyService
import nl.komponents.kovenant.Promise

class UITelephonyServiceImpl(private val platformTelephonyService: PlatformTelephonyService) : UITelephonyService {
    override fun getDevicePhoneNumber(): Promise<String?, Exception> = platformTelephonyService.getDevicePhoneNumber()

    override fun supportsMakingCalls(): Boolean = platformTelephonyService.supportsMakingCalls()

    override fun callContact(userId: UserId) = platformTelephonyService.callContact(userId)
}