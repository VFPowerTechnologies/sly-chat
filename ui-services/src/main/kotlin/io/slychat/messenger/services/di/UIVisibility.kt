package io.slychat.messenger.services.di

import javax.inject.Qualifier

/** Observable indicating current UI visibility. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UIVisibility