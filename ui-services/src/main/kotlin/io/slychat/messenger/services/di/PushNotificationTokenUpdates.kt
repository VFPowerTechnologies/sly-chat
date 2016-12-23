package io.slychat.messenger.services.di

import javax.inject.Qualifier

/** Observable indicating push notification token updates. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PushNotificationTokenUpdates