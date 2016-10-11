package io.slychat.messenger.core.http.api

import io.slychat.messenger.core.http.HttpResponse

class ServerErrorException(response: HttpResponse, val error: ApiError?) :
    ApiException("${response.code} error from server: ${error?.message ?: "No error"}", response)
