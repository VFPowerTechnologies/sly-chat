package io.slychat.messenger.services.di.annotations

import javax.inject.Qualifier

/** Http access for misc external services. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ExternalHttp