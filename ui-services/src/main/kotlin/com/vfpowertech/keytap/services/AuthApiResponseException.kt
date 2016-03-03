package com.vfpowertech.keytap.services

class AuthApiResponseException(val errorMessage: String): RuntimeException(errorMessage)