package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.services.AlwaysOnNetworkStatusService
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.NetworkStatusService
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ApplicationModule(
    @get:javax.inject.Singleton
    @get:Provides
    val providesApplication: KeyTapApplication
) {
    //TODO here for simplicity for the moment
    @Singleton
    @Provides
    fun providesNetworkStatusService(): NetworkStatusService = AlwaysOnNetworkStatusService()
}