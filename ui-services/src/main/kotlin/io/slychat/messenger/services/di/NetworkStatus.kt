package io.slychat.messenger.services.di

import javax.inject.Qualifier

/** Observable indicating current network status. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NetworkStatus