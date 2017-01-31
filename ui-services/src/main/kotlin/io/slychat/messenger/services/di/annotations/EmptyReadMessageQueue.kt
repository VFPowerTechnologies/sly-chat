package io.slychat.messenger.services.di.annotations

import javax.inject.Qualifier

/** Observable indicating that the read message queue is empty. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EmptyReadMessageQueue