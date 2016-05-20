package io.slychat.messenger.core.http.api.infoservice

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class InfoServiceAsyncClient() {
    private fun newClient() = InfoServiceClient(io.slychat.messenger.core.http.JavaHttpClient())

    fun getGeoLocationInfo(): Promise<GeoLocationInfo?, Exception> = task {
        newClient().getGeoLocationInfo()
    }
}