import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class GenBuildConfig extends DefaultTask {
    private static def componentTypes = [
        'registration',
        'platformInfo',
        'login',
        'contacts',
        'messenger',
        'history'
    ]

    private static def componentEnumTypes = componentTypes.collect { camelCaseToStaticConvention(it) }

    static String camelCaseToStaticConvention(String s) {
        s.replaceAll(~/(?<!^)([A-Z])/) { all, g1 -> "_$g1" }.toUpperCase()
    }

    //String.toBoolean returns false for anything that isn't 'true', which is stupid since it won't throw on typos
    private static boolean stringToBoolean(String s) {
        def l = s.toLowerCase()
        if (l == 'true')
            return true
        else if (l == 'false')
            return false
        else
            throw new IllegalArgumentException("Expected boolean value, got $s")
    }

    static String getEnumValue(Properties settings, boolean debug, String setting, List<String> validValues, String defaultValue) {
        List<String> keys = []
        if (debug)
            keys.add("debug.$setting")

        keys.add(setting)

        String value;
        for (key in keys) {
            value = settings.getProperty(key)?.toUpperCase()
            if (value != null)
                break
        }

        if (value == null) {
            if (defaultValue == null)
                throw new InvalidUserDataException("No $setting setting found in properties file")
            return defaultValue
        }

        if (!validValues.contains(value))
            throw new InvalidUserDataException("Invalid value for $setting: $value")

        return value
    }

    private File projectRoot = project.projectDir

    @InputFile
    File defaultPropertiesPath = new File(projectRoot, 'default.properties')

    @InputFile
    File localPropertiesPath = new File(projectRoot, 'local.properties')

    File generateRoot = new File(projectRoot, 'generated')

    File srcRoot = new File(generateRoot, 'src/main/java')
    //kotlinc seems to really freak out if this isn't in the proper dir
    //`java.lang.IllegalStateException: Requested BuildConfig, got com.vfpowertech.keytap.core.BuildConfig`
    File outputDirectory = new File(srcRoot, 'com/vfpowertech/keytap/core')

    @OutputFile
    File outputFile = new File(outputDirectory, 'BuildConfig.java')

    @OutputFile
    File jsOutputFile = new File(projectRoot, 'ui/ui/js/build-config.js')

    private Properties getSettingProperties() {
        def props = new Properties()
        defaultPropertiesPath.newReader().withReader { props.load(it) }

        localPropertiesPath.newReader().withReader { props.load(it) }

        return props
    }

    private static String findValueForKeys(Properties settings, String settingName, List<String> keys) {
        def v
        for (key in keys) {
            v = settings.getProperty(key)
            if (v != null)
                break
        }

        if (v == null)
            throw new InvalidUserDataException("Missing setting $settingName in properties file")

        return v
    }

    @TaskAction
    void run() {
        def rootProject = project.rootProject

        def veProperties = new Properties()
        veProperties.setProperty('runtime.references.strict', 'true')

        def ve = new VelocityEngine(veProperties)

        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, 'classpath')
        ve.setProperty('classpath.resource.loader.class', ClasspathResourceLoader.class.name)
        ve.init()

        def vc = new VelocityContext()
        def settings = getSettingProperties()

        vc.put("version", rootProject.version)

        def debug = stringToBoolean(settings.getProperty('debug'))
        vc.put('debug', debug)

        vc.put('uiServiceType', getEnumValue(settings, debug, 'uiServiceType', ['DUMMY', 'REAL'], null))

        //adds <platform><SettingName> to context
        for (setting in ['httpApiServer']) {
            for (platform in ['desktop', 'android']) {
                List<String> keys = []

                if (debug) {
                    keys.add("debug.${platform}.$setting")
                    keys.add("debug.$setting")
                }
                keys.add("${platform}.$setting")
                keys.add("httpApiServer")

                String url;
                for (key in keys) {
                    url = settings.getProperty(key)
                    if (url != null)
                        break
                }

                if (url == null)
                    throw new InvalidUserDataException("Missing setting $setting in properties file")

                vc.put("${platform}${setting.capitalize()}", url)
            }
        }

        List<String> keys = []
        if (debug)
            keys.add('debug.uiServiceType')
        keys.add('uiServiceType')
        String componentTypeDefault = findValueForKeys(settings, 'uiServiceType', keys).toUpperCase()

        def componentTypes = componentTypes.collectEntries { component ->
            def value = getEnumValue(settings, debug, "uiServiceType.$component", ['DUMMY', 'REAL'], componentTypeDefault)
            def key = camelCaseToStaticConvention(component)
            [(key): value.toUpperCase()]
        }
        vc.put('componentEnumTypes', componentEnumTypes)
        vc.put('componentTypes', componentTypes.entrySet())

        outputFile.withWriter {
            def vt = ve.getTemplate('/BuildConfig.java.vm')

            vt.merge(vc, it)
        }

        jsOutputFile.withWriter {
            def vt = ve.getTemplate('/build-config.js.vm')

            vt.merge(vc, it)
        }
    }
}
