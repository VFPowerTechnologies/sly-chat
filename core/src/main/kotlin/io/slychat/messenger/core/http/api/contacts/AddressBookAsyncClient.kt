package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface AddressBookAsyncClient {
    fun get(userCredentials: UserCredentials): Promise<GetAddressBookResponse, Exception>

    fun update(userCredentials: UserCredentials, request: UpdateAddressBookRequest): Promise<Unit, Exception>
}