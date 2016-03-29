package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.services.PlatformContacts
import com.vfpowertech.keytap.services.ui.PlatformNotificationService
import com.vfpowertech.keytap.services.ui.PlatformTelephonyService
import com.vfpowertech.keytap.services.ui.UIPlatformInfoService
import com.vfpowertech.keytap.services.ui.UIWindowService
import dagger.Module
import dagger.Provides
import rx.Scheduler
import javax.inject.Singleton

@Module
class PlatformModule(
    @get:Singleton
    @get:Provides
    val providesPlatformInfoService: UIPlatformInfoService,

    @get:Provides
    val providesServerUrls: BuildConfig.ServerUrls,

    @get:Singleton
    @get:Provides
    val providesPlatformInfo: PlatformInfo,

    @get:Singleton
    @get:Provides
    val providesPlatformTelephonyService: PlatformTelephonyService,

    @get:Singleton
    @get:Provides
    val providesWindowService: UIWindowService,

    @get:Singleton
    @get:Provides
    val providesPlatformContacts: PlatformContacts,

    @get:Singleton
    @get:dagger.Provides
    val providesPlatformNotificationService: PlatformNotificationService,

    @get:Singleton
    @get:Provides
    val providesScheduler: Scheduler
)

