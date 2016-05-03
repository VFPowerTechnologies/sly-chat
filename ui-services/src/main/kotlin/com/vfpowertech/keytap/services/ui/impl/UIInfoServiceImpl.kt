package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.http.api.infoService.InfoServiceAsyncClient
import com.vfpowertech.keytap.services.ui.UIInfoService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map

class UIInfoServiceImpl: UIInfoService {
    private val infoServiceClient = InfoServiceAsyncClient()

    override fun getGeoLocation(): Promise<String?, Exception> {
        return infoServiceClient.getGeoLocationInfo() map { response ->
            if (response === null)
                null
            else
                response.country
        }
    }

}