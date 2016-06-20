package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.http.api.infoservice.InfoServiceAsyncClient
import io.slychat.messenger.services.ui.UIInfoService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map

class UIInfoServiceImpl(
    private val infoServiceClient: InfoServiceAsyncClient
) : UIInfoService {
    override fun getGeoLocation(): Promise<String?, Exception> {
        return infoServiceClient.getGeoLocationInfo() map { response ->
            if (response === null)
                null
            else
                response.country
        }
    }
}