import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*

open class GenBuildConfig : DefaultTask() {
    companion object {
        private val componentTypes = listOf(
            "registration",
            "platformInfo",
            "login",
            "contacts",
            "messenger",
            "history",
            "networkStatus"
        )

        private val componentEnumTypes = componentTypes.map { camelCaseToStaticConvention(it) }

        private fun camelCaseToStaticConvention(s: String): String =
            "(?<!^)([A-Z])".toRegex().replace(s) { m ->
                "_" + m.groups[1]!!.value
            }.toUpperCase()

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

        private fun getEnumValue(settings: Properties, debug: Boolean, setting: String, validValues: List<String>, defaultValue: String?): String {
            val keys = ArrayList<String>()
            if (debug)
                keys.add("debug.$setting")

            keys.add(setting)

            val value = getSetting(settings, keys)?.toUpperCase()

            if (value == null) {
                if (defaultValue == null)
                    throw InvalidUserDataException("No $setting setting found in properties file")
                return defaultValue
            }

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

        private fun stringToInetSocketAddress(s: String): String {
            val parts = s.split(':', limit = 2)
            if (parts.size != 2)
                throw InvalidUserDataException("Invalid address: $s")

            val host = parts[0]
            val port = parts[1]

            return "new InetSocketAddress(\"$host\", $port)"
        }
    }

    private val projectRoot = project.projectDir

    @InputFile
    val defaultPropertiesPath = File(projectRoot, "default.properties")

    @InputFile
    val localPropertiesPath = File(projectRoot, "local.properties")

    //TODO maybe let these be overriden as settings (or set as relative paths to the project root)
    val generateRoot = File(projectRoot, "generated")

    val srcRoot = File(generateRoot, "src/main/java")

    //kotlinc seems to really freak out if this isn't in the proper dir
    //`java.lang.IllegalStateException: Requested BuildConfig, got com.vfpowertech.keytap.core.BuildConfig`
    val outputDirectory = File(srcRoot, "io/slychat/messenger/core")

    @OutputFile
    val outputFile = File(outputDirectory, "BuildConfig.java")

    @OutputFile
    val jsOutputFile = File(projectRoot, "ui/ui/js/build-config.js")

    private fun getSettingProperties(): Properties {
        val props = Properties()

        defaultPropertiesPath.reader().use { props.load(it) }

        localPropertiesPath.reader().use { props.load(it) }

        return props
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

        val enableDatabaseEncryption = stringToBoolean(findValueForKey(settings, "enableDatabaseEncryption", debug))
        vc.put("enableDatabaseEncryption", enableDatabaseEncryption);

        vc.put("uiServiceType", getEnumValue(settings, debug, "uiServiceType", listOf("DUMMY", "REAL"), null))

        vc.put("relayKeepAliveIntervalMs", findMsForKey(settings, "relayServer.keepAlive", debug))

        //adds <platform><SettingName> to context
        val urlSettings = listOf(
            "httpApiServer" to { s: String -> s },
            "relayServer" to { s -> stringToInetSocketAddress(s) }
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
                keys.add("$setting")

                val url = findValueForKeys(settings, setting, keys)

                vc.put("$platform${setting.capitalize()}", transform(url))
            }
        }

        val componentTypeDefault = findValueForKey(settings, "uiServiceType", debug).toUpperCase()

        val componentTypes = componentTypes.map { component ->
            val value = getEnumValue(settings, debug, "uiServiceType.$component", listOf("DUMMY", "REAL"), componentTypeDefault)
            val key = camelCaseToStaticConvention(component)
            key to value.toUpperCase()
        }.toMap()

        vc.put("componentEnumTypes", componentEnumTypes)
        vc.put("componentTypes", componentTypes.entries)

        outputFile.writer().use {
            val vt = ve.getTemplate("/BuildConfig.java.vm")

            vt.merge(vc, it)
        }

        jsOutputFile.writer().use {
            val vt = ve.getTemplate("/build-config.js.vm")

            vt.merge(vc, it)
        }
    }
}