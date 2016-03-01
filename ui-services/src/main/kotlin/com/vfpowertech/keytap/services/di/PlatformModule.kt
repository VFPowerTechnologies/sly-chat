package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.services.ui.PlatformInfoService
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
    val providesScheduler: Scheduler
)

