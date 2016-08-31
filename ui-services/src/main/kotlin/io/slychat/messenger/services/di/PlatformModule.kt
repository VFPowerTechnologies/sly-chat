package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.PlatformTelephonyService
import io.slychat.messenger.services.ui.UILoadService
import io.slychat.messenger.services.ui.UIPlatformInfoService
import io.slychat.messenger.services.ui.UIPlatformService
import io.slychat.messenger.services.ui.UIWindowService
import rx.Observable
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
    @get:Provides
    val providesPlatformNotificationService: PlatformNotificationService,

    @get:Singleton
    @get:Provides
    val providesPlatformService: UIPlatformService,

    @get:Singleton
    @get:Provides
    val providesLoadService: UILoadService,

    @get:Singleton
    @get:Provides
    @get:UIVisibility
    val uiVisibility: Observable<Boolean>,

    @get:Singleton
    @get:Provides
    @get:NetworkStatus
    val networkStatus: Observable<Boolean>,

    @get:Singleton
    @get:Provides
    val providesScheduler: Scheduler
)

