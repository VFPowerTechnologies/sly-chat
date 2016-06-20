package io.slychat.messenger.services.di

import javax.inject.Qualifier

/** Http access for Sly services. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SlyHttp

/** Http access for misc external services. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ExternalHttp