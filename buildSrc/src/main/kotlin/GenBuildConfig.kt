import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.*

@Suppress("unused")
open class GenBuildConfig : DefaultTask() {
    companion object {
        /**
         * Converts the given string to a boolean.
         *
         * @throws IllegalArgumentException If s is not one of true|false.
         */
        //String.toBoolean returns false for anything that isn't 'true', which is stupid since it won't throw on typos
        private fun stringToBoolean(s: String): Boolean =
            when (s.toLowerCase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Expected boolean value, got $s")
            }

        private fun getSetting(settings: Properties, keys: List<String>): String? {
            keys.forEach { key ->
                val value = settings.getProperty(key)
                if (value != null)
                    return value
            }

            return null
        }

        private fun getEnumValue(settings: Properties, setting: String, validValues: List<String>, debug: Boolean): String {
            val value = findValueForKey(settings, setting, debug)

            if (value !in validValues)
                throw InvalidUserDataException("Invalid value for $setting: $value")

            return value
        }
        /**
         * Returns the value found for a key; if debug is enabled, checks
         * debug.<key> first. Throws InvalidUserDataException if key isn't found.
         */
        private fun findValueForKey(settings: Properties, key: String, debug: Boolean): String {
            //TODO this is the same as the enum code; refactor
            val keys = ArrayList<String>()
            if (debug)
                keys.add("debug.$key")

            keys.add(key)

            val value = getSetting(settings, keys)

            return value ?: throw InvalidUserDataException("Missing setting $key in properties file")
        }

        private val timeSettingRegex = "(\\d+)(ms|s|m|h|d)".toRegex()

        /**
         * Returns milliseconds from a given key. Checks debug.<key> if debug is true.
         */
        private fun findMsForKey(settings: Properties, key: String, debug: Boolean): Long {
            val s = findValueForKey(settings, key, debug)

            val m = timeSettingRegex.matchEntire(s) ?: throw InvalidUserDataException("Error processing setting $key: $s is an invalid time format string")

            val v = m.groups[1]!!.value.toLong()
            val unit = m.groups[2]!!.value
            val conversionAmount = getMsConversationAmount(unit)

            return v * conversionAmount
        }

        private fun findBoolForKey(settings: Properties, key: String, debug: Boolean): Boolean =
            stringToBoolean(findValueForKey(settings, key, debug))

        private fun getMsConversationAmount(unit: String): Long = when (unit) {
            "ms" -> 1
            "s" -> 1000
            "m" -> 60 * 1000
            "h" -> 60 * 60 * 1000
            "d" -> 24 * 60 * 60 * 1000
            else -> throw IllegalArgumentException("Invalid time unit: $unit")
        }

        /** Returns the first found key's value. If none of the keys are present, throws InvalidUserDataException. */
        private fun findValueForKeys(settings: Properties, settingName: String, keys: List<String>): String {
            val v = getSetting(settings, keys)
            return v ?: throw InvalidUserDataException("Missing setting $settingName in properties file")
        }

        private fun isIpAddress(s: String): Boolean {
            return s.matches("\\d+.\\d+.\\d+.\\d+".toRegex())
        }

        private fun stringToInetSocketAddress(s: String): String {
            val parts = s.split(':', limit = 2)
            if (parts.size != 2)
                throw InvalidUserDataException("Invalid address: $s")

            val host = parts[0]
            val port = parts[1]

            val constructor = if (isIpAddress(host))
                "new InetSocketAddress"
            else
                "InetSocketAddress.createUnresolved"

            return "$constructor(\"$host\", $port)"
        }

        /** Parses a sentry DSN. Returns null for malformed DSNs. */
        private fun sentryDSNFromString(s: String): String {
            try {
                val uri = URI(s)

                val userInfo = uri.userInfo
                val parts = userInfo.split(':', limit = 2)
                if (parts.size != 2)
                    throw InvalidUserDataException("Invalid auth for sentryDsn")

                val publicKey = parts[0]
                val privateKey = parts[1]

                val path = uri.path
                if (path.length < 2)
                    throw InvalidUserDataException("Missing projectId for sentryDsn")

                val projectId = path.substring(1)

                return """new DSN("$publicKey", "$privateKey", "$projectId", "${uri.scheme}", "${uri.host}", ${uri.port})"""
            }
            catch (e: URISyntaxException) {
                throw InvalidUserDataException("Invalid URI syntax for sentryDsn: $s")
            }
        }
    }

    private val projectRoot = project.projectDir

    private val debugLogSettings = "/debug-sly-logger.properties"

    private val releaseLogSettings = "/release-sly-logger.properties"

    @InputFile
    val defaultPropertiesPath = File(projectRoot, "default.properties")

    @InputFile
    val localPropertiesPath = File(projectRoot, "local.properties")

    //for version
    @InputFile
    val gradlePropertiesPath = File(project.rootDir, "gradle.properties")

    //this is kinda hacky...
    @InputFile
    val buildConfigJavaTemplate = File(projectRoot, "buildSrc/src/main/resources/SlyBuildConfig.java.vm")

    @InputFile
    val buildConfigJSTemplate = File(projectRoot, "buildSrc/src/main/resources/build-config.js.vm")

    @Input
    val releaseSettingsTemplate = File(projectRoot, "buildSrc/src/main/resources/release-sly-logger.properties")

    @Input
    val debugLogSettingsTemplate = File(projectRoot, "buildSrc/src/main/resources/debug-sly-logger.properties")

    //TODO maybe let these be overriden as settings (or set as relative paths to the project root)
    val generateRoot = File(projectRoot, "generated")

    val srcRoot = File(generateRoot, "src/main/java")

    //kotlinc seems to really freak out if this isn't in the proper dir
    //`java.lang.IllegalStateException: Requested BuildConfig, got com.vfpowertech.keytap.core.BuildConfig`
    val outputDirectory = File(srcRoot, "io/slychat/messenger/core")

    @OutputFile
    val outputFile = File(outputDirectory, "SlyBuildConfig.java")

    @OutputFile
    val jsOutputFile = File(projectRoot, "ui/ui/js/build-config.js")

    @OutputFile
    val androidLogSettings = File(projectRoot, "android/src/main/resources/sly-logger.properties")

    @OutputFile
    val desktopLogSettings = File(projectRoot, "desktop/src/main/resources/sly-logger.properties")

    //why
    @OutputFile
    val iosLogSettings = File(projectRoot, "ios/xcode/sly-logger.properties")

    private fun getSettingProperties(): Properties {
        val props = Properties()

        defaultPropertiesPath.reader().use { props.load(it) }

        localPropertiesPath.reader().use { props.load(it) }

        return props
    }

    private fun getCACertificate(debug: Boolean): String {
        val filename = "certs/ca-cert%s.pem".format(if (debug) ".debug" else "")

        return File(projectRoot, filename).readText()
    }

    private fun convertToByteArrayNotation(s: String): String {
        return s.toByteArray().map(Byte::toString).joinToString(",", "{", "}")
    }

    private fun writeTemplate(ve: VelocityEngine, vc: VelocityContext, templatePath: String, outputPath: File) {
        outputPath.writer().use {
            val vt = ve.getTemplate(templatePath)

            vt.merge(vc, it)
        }
    }

    @TaskAction
    fun run() {
        val rootProject = project.rootProject

        val veProperties = Properties()
        veProperties.setProperty("runtime.references.strict", "true")

        val ve = VelocityEngine(veProperties)

        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath")
        ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader::class.java.name)
        ve.init()

        val vc = VelocityContext()
        val settings = getSettingProperties()

        vc.put("version", rootProject.version)

        val debug = stringToBoolean(settings.getProperty("debug"))
        vc.put("debug", debug)

        val enableDatabaseEncryption = findBoolForKey(settings, "enableDatabaseEncryption", debug)
        vc.put("enableDatabaseEncryption", enableDatabaseEncryption);

        val enableConfigEncryption = findBoolForKey(settings, "enableConfigEncryption", debug)
        vc.put("enableConfigEncryption", enableConfigEncryption)

        vc.put("relayKeepAliveIntervalMs", findMsForKey(settings, "relayServer.keepAlive", debug))

        vc.put("disableCertificateVerification", findBoolForKey(settings, "tls.disableCertificateVerification", debug))
        vc.put("disableHostnameVerification", findBoolForKey(settings, "tls.disableHostnameVerification", debug))
        vc.put("disableCRLVerification", findBoolForKey(settings, "tls.disableCRLVerification", debug))

        //adds <platform><SettingName> to context
        val urlSettings = listOf(
            "httpApiServer" to { s: String -> s },
            "relayServer" to { s -> stringToInetSocketAddress(s) },
            "fileServer" to { s: String -> s }
        )

        for (entry in urlSettings) {
            val (setting, transform) = entry

            for (platform in listOf("desktop", "android")) {
                val keys = ArrayList<String>()

                if (debug) {
                    keys.add("debug.$platform.$setting")
                    keys.add("debug.$setting")
                }

                keys.add("$platform.$setting")
                keys.add(setting)

                val url = findValueForKeys(settings, setting, keys)

                vc.put("$platform${setting.capitalize()}", transform(url))
            }
        }

        val maybeSentryDsn = findValueForKey(settings, "sentryDsn", debug)
        val sentryDsn = if (maybeSentryDsn.isEmpty())
            "null"
        else
            sentryDSNFromString(maybeSentryDsn)

        vc.put("sentryDsn", sentryDsn)

        val cert = getCACertificate(debug)

        val inline = convertToByteArrayNotation(cert)
        vc.put("caCert", inline)

        writeTemplate(ve, vc, "/SlyBuildConfig.java.vm", outputFile)
        writeTemplate(ve, vc, "/build-config.js.vm", jsOutputFile)

        writeLogSettings(ve, settings, debug)
    }

    private fun writeLogSettings(ve: VelocityEngine, settings: Properties, debug: Boolean) {
        val logSettingsType = getEnumValue(settings, "logSettings", listOf("release", "debug"), debug)

        val logVc = VelocityContext()
        val dispatcherLogLevel = findValueForKey(settings, "dispatcherLogLevel", debug)
        logVc.put("dispatcherLogLevel", dispatcherLogLevel)

        val selectedLogSettings = if (logSettingsType == "release")
            releaseLogSettings
        else
            debugLogSettings

        writeTemplate(ve, logVc, selectedLogSettings, androidLogSettings)
        writeTemplate(ve, logVc, selectedLogSettings, desktopLogSettings)
        writeTemplate(ve, logVc, selectedLogSettings, iosLogSettings)
    }
}