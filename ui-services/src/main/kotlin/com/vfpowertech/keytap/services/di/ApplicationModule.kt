package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.services.AuthenticationService
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.UserPathsGenerator
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ApplicationModule(
    @get:Singleton
    @get:Provides
    val providesApplication: KeyTapApplication
) {
    @Singleton
    @Provides
    fun providesAuthenticationService(serverUrls: BuildConfig.ServerUrls, userPathsGenerator: UserPathsGenerator): AuthenticationService =
        AuthenticationService(serverUrls.API_SERVER, userPathsGenerator)

    //this is here we can check for the existence of cached data on startup without establishing a user session
    @Singleton
    @Provides
    fun providesUserPathsGenerator(platformInfo: PlatformInfo): UserPathsGenerator = UserPathsGenerator(platformInfo)
}