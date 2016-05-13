package io.slychat.messenger.services.di

import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.services.AuthenticationService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.UserPathsGenerator
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ApplicationModule(
    @get:Singleton
    @get:Provides
    val providesApplication: SlyApplication
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