package io.slychat.messenger.core.http.api

import io.slychat.messenger.core.http.HttpResponse

class UnexpectedResponseException(response: HttpResponse) :
    ApiException("Unexpected response from server: ${response.code}", response)