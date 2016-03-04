package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.persistence.json.JsonStartupInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.StartupInfo
import com.vfpowertech.keytap.services.ui.UIConfigService
import nl.komponents.kovenant.Promise
import java.io.File

class UIConfigServiceImpl(private val path: File) : UIConfigService {
    private val startupInfoPersistenceManager = JsonStartupInfoPersistenceManager(path)

    override fun getStartupInfo(): Promise<StartupInfo?, Exception> =
        startupInfoPersistenceManager.retrieve()

    override fun setStartupInfo(startupInfo: StartupInfo): Promise<Unit, Exception> =
        startupInfoPersistenceManager.store(startupInfo)
}