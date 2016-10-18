package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface PlatformTelephonyService {
    /** Return the device's telephone number, if available. */
    fun getDevicePhoneNumber(): Promise<String?, Exception>

    fun supportsMakingCalls(): Boolean

    //done this way to avoid letting the UI choose the phone number called (eg: incase of some js injection or some other vuln)
    //should never be called unless supportsMakingCalls returns true
    fun callContact(userId: UserId)
}