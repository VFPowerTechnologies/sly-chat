package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.services.*
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ApplicationModule(
    @get:Singleton
    @get:Provides
    val providesApplication: KeyTapApplication
) {
    //TODO here for simplicity for the moment
    @Singleton
    @Provides
    fun providesNetworkStatusService(): NetworkStatusService = AlwaysOnNetworkStatusService()

    @Singleton
    @Provides
    fun providesAuthenticationService(serverUrls: BuildConfig.ServerUrls): AuthenticationService =
        AuthenticationService(serverUrls.API_SERVER)

    //this is here we can check for the existence of cached data on startup without establishing a user session
    @Singleton
    @Provides
    fun providesUserPathsGenerator(platformInfo: PlatformInfo): UserPathsGenerator = UserPathsGenerator(platformInfo)
}