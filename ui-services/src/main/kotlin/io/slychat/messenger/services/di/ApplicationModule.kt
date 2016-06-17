package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.crypto.tls.CachingCRLFetcher
import io.slychat.messenger.core.crypto.tls.JavaHttpCRLFetcher
import io.slychat.messenger.core.http.HttpClientConfig
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.JavaHttpClientFactory
import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.services.AuthenticationService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.UserPathsGenerator
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Singleton

@Module
class ApplicationModule(
    @get:Singleton
    @get:Provides
    val providesApplication: SlyApplication
) {
    @Singleton
    @Provides
    fun providesAuthenticationService(
        serverUrls: BuildConfig.ServerUrls,
        userPathsGenerator: UserPathsGenerator,
        @SlyHttp httpClientFactory: HttpClientFactory
    ): AuthenticationService =
        AuthenticationService(serverUrls.API_SERVER, httpClientFactory, userPathsGenerator)

    @Singleton
    @Provides
    fun providesSSLConfigurator(): SSLConfigurator {
        val crlFetcher = CachingCRLFetcher(JavaHttpCRLFetcher())
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(BuildConfig.caCert)) as X509Certificate
        return SSLConfigurator(
            cert,
            crlFetcher,
            BuildConfig.TLS_DISABLE_HOSTNAME_VERIFICATION,
            false,
            BuildConfig.TLS_DISABLE_CERTIFICATE_VERIFICATION
        )
    }

    //this is here we can check for the existence of cached data on startup without establishing a user session
    @Singleton
    @Provides
    fun providesUserPathsGenerator(platformInfo: PlatformInfo): UserPathsGenerator = UserPathsGenerator(platformInfo)

    @Provides
    fun providesHttpClientConfig(): HttpClientConfig = HttpClientConfig(3000, 3000)

    @Provides
    @SlyHttp
    fun providesSlyHttpClientFactory(
        config: HttpClientConfig,
        sslConfigurator: SSLConfigurator
    ): HttpClientFactory {
        return JavaHttpClientFactory(config, sslConfigurator)
    }

    @Provides
    @ExternalHttp
    fun providesExternalHttpClientFactory(config: HttpClientConfig): HttpClientFactory {
        return JavaHttpClientFactory(config, null)
    }
}