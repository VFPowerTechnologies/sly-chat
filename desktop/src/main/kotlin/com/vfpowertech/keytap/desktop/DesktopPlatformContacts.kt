package com.vfpowertech.keytap.desktop

import com.vfpowertech.keytap.services.PlatformContact
import com.vfpowertech.keytap.services.PlatformContacts
import nl.komponents.kovenant.Promise
import java.util.*

class DesktopPlatformContacts : PlatformContacts {
    override fun fetchContacts(): Promise<List<PlatformContact>, Exception> = Promise.ofSuccess(ArrayList())
}