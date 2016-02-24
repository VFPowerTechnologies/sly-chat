package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.jsbridge.core.dispatcher.WebEngineInterface
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.ui.services.PlatformInfoService
import dagger.Module
import dagger.Provides
import rx.Scheduler
import javax.inject.Singleton

@Module
class PlatformModule(
    @get:Singleton
    @get:Provides
    val providesPlatformInfoService: PlatformInfoService,

    @get:Provides
    val providesServerUrls: BuildConfig.ServerUrls,

    @get:Singleton
    @get:Provides
    val providesPlatformInfo: PlatformInfo,

    @get:Singleton
    @get:Provides
    val providesWebEngineInterface: WebEngineInterface,

    @get:Singleton
    @get:Provides
    val providesScheduler: Scheduler
)

