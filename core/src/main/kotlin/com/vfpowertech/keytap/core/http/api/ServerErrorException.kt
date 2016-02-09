package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

class ServerErrorException(val response: HttpResponse) :
    RuntimeException("Error from server: ${response.code}")
