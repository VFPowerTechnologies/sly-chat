package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.keytap.ui.services.AlwaysOnNetworkStatusService
import com.vfpowertech.keytap.ui.services.KeyTapApplication
import com.vfpowertech.keytap.ui.services.NetworkStatusService
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