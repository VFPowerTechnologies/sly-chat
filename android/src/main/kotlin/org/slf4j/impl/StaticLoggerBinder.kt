package org.slf4j.impl

@Suppress("unused")
object StaticLoggerBinder : BaseStaticLoggerBinder() {
    //required; else you get a warning at runtime that your binding only supports <= 1.5.5
    //see http://slf4j.org/faq.html#version_checks
    @Suppress("unused")
    @JvmField
    val REQUESTED_API_VERSION: String = "1.6.99"

    //required
    @JvmStatic
    fun getSingleton(): StaticLoggerBinder {
        return this
    }

    override val platformLogger: PlatformLogger
        get() = AndroidPlatformLogger()
}
