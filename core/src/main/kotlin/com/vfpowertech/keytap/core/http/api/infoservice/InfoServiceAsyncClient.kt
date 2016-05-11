package com.vfpowertech.keytap.core.http.api.infoservice

import com.vfpowertech.keytap.core.http.JavaHttpClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class InfoServiceAsyncClient() {
    private fun newClient() = InfoServiceClient(JavaHttpClient())

    fun getGeoLocationInfo(): Promise<GeoLocationInfo?, Exception> = task {
        newClient().getGeoLocationInfo()
    }
}