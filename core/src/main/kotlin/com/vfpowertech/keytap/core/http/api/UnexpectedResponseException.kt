package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

class UnexpectedResponseException(response: HttpResponse) :
    ApiException("Unexpected response from server: ${response.code}", response)