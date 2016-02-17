package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

class ServerErrorException(response: HttpResponse, val error: ApiError?) :
    ApiException("${response.code} error from server: ${error?.message ?: "No error"}", response)
