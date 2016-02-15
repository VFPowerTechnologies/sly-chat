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

    private static String getEnumValue(Properties settings, boolean debug, String setting, List<String> validValues) {
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

        if (value == null)
            throw new InvalidUserDataException("No $setting setting found in properties file")

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

    File outputDirectory = new File(generateRoot, 'src/main/java')

    @OutputFile
    File outputFile = new File(outputDirectory, 'BuildConfig.java')

    private Properties getSettingProperties() {
        def props = new Properties()
        defaultPropertiesPath.newReader().withReader { props.load(it) }

        localPropertiesPath.newReader().withReader { props.load(it) }

        return props
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

        vc.put('uiServiceType', getEnumValue(settings, debug, 'uiServiceType', ['DUMMY', 'REAL']))

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
                    throw new IllegalArgumentException("Missing setting $setting in properties file")

                vc.put("${platform}${setting.capitalize()}", url)
            }
        }

        def vt = ve.getTemplate('/BuildConfig.java.vm')

        outputFile.withWriter {
            vt.merge(vc, it)
        }
    }
}
