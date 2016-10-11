package io.slychat.messenger.core.http.api.infoservice

import io.slychat.messenger.core.http.HttpClientFactory
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class InfoServiceAsyncClient(private val factory: HttpClientFactory) {
    private fun newClient() = InfoServiceClient(factory.create())

    fun getGeoLocationInfo(): Promise<GeoLocationInfo?, Exception> = task {
        newClient().getGeoLocationInfo()
    }
}