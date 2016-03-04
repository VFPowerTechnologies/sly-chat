package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import com.vfpowertech.keytap.core.persistence.StartupInfo
import nl.komponents.kovenant.Promise

/** Fetch/retrieve application configuration values. */
@JSToJavaGenerate("ConfigService")
interface UIConfigService {
    fun getStartupInfo(): Promise<StartupInfo?, Exception>
    fun setStartupInfo(startupInfo: StartupInfo): Promise<Unit, Exception>
}