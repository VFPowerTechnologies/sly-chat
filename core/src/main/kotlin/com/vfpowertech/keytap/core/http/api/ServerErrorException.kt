package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

class ServerErrorException(response: HttpResponse) :
    ApiException("Error from server: ${response.code}", response)
