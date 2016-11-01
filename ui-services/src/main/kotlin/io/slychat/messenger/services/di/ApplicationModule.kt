package io.slychat.messenger.services.di

import dagger.Module
import dagger.Provides
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.crypto.tls.CachingCRLFetcher
import io.slychat.messenger.core.crypto.tls.JavaHttpCRLFetcher
import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import io.slychat.messenger.core.div
import io.slychat.messenger.core.http.HttpClientConfig
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.http.JavaHttpClientFactory
import io.slychat.messenger.core.http.api.authentication.AuthenticationAsyncClientImpl
import io.slychat.messenger.core.persistence.InstallationDataPersistenceManager
import io.slychat.messenger.core.persistence.json.JsonInstallationDataPersistenceManager
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthenticationService
import io.slychat.messenger.services.auth.AuthenticationServiceImpl
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.config.FileConfigStorage
import io.slychat.messenger.services.config.JsonConfigBackend
import io.slychat.messenger.services.contacts.RxPromiseTimerFactory
import io.slychat.messenger.services.contacts.PromiseTimerFactory
import rx.Observable
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
    fun providesLocalAccountDirectory(
        userPathsGenerator: UserPathsGenerator
    ): LocalAccountDirectory {
        return FileSystemLocalAccountDirectory(userPathsGenerator)
    }

    @Singleton
    @Provides
    fun providesAuthenticationService(
        serverUrls: SlyBuildConfig.ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory,
        localAccountDirectory: LocalAccountDirectory
    ): AuthenticationService {
        val authenticationClient = AuthenticationAsyncClientImpl(serverUrls.API_SERVER, httpClientFactory)
        return AuthenticationServiceImpl(
            authenticationClient,
            localAccountDirectory
        )
    }

    @Singleton
    @Provides
    fun providesSSLConfigurator(): SSLConfigurator {
        val crlFetcher = CachingCRLFetcher(JavaHttpCRLFetcher())
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(SlyBuildConfig.caCert)) as X509Certificate
        return SSLConfigurator(
            cert,
            crlFetcher,
            SlyBuildConfig.TLS_DISABLE_HOSTNAME_VERIFICATION,
            SlyBuildConfig.TLS_DISABLE_CRL_VERIFICATION,
            SlyBuildConfig.TLS_DISABLE_CERTIFICATE_VERIFICATION
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

    @Singleton
    @Provides
    fun providesAppConfigService(platformInfo: PlatformInfo): AppConfigService {
        val appConfPath = platformInfo.appFileStorageDirectory / "app-conf.json"
        val storage = FileConfigStorage(appConfPath)
        val backend = JsonConfigBackend("app-config", storage)
        return AppConfigService(backend)
    }

    @Singleton
    @Provides
    fun providesTimerFactory(): PromiseTimerFactory {
        return RxPromiseTimerFactory()
    }

    @Singleton
    @Provides
    fun providesInstallationDataPersistenceManager(
        platformInfo: PlatformInfo
    ): InstallationDataPersistenceManager {
        val path = platformInfo.appFileStorageDirectory / "installation-data.json"
        return JsonInstallationDataPersistenceManager(path)
    }

    @Singleton
    @Provides
    fun providesVersionChecker(
        serverUrls: SlyBuildConfig.ServerUrls,
        @SlyHttp httpClientFactory: HttpClientFactory,
        @NetworkStatus networkAvailable: Observable<Boolean>
    ): VersionChecker {
        val factory = HttpClientVersionAsyncClientFactory(serverUrls.API_SERVER, httpClientFactory)

        return HttpVersionChecker(
            SlyBuildConfig.VERSION,
            networkAvailable,
            factory
        )
    }
}