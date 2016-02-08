package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

class UnexpectedResponseException(val response: HttpResponse) :
    RuntimeException("Unexpected response from server: ${response.responseCode}")