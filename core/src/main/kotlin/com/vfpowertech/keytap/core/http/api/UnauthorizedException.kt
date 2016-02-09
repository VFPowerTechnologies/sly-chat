package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

/** Invalid auth token. Represented as a 401 response code from a request. */
class UnauthorizedException(response: HttpResponse) : ApiException("Unauthorized request", response)
