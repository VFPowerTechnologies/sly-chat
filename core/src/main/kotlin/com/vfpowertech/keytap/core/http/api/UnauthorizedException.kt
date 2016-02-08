package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

/** Invalid auth token. Represented as a 401 response code from a request. */
class UnauthorizedException(val response: HttpResponse) : RuntimeException("Unauthorized request")
