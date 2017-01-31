import com.github.zafarkhaja.semver.Version
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*

//for some reason groovy can't access nested enums
enum class VersionType {
    PATCH,
    MINOR,
    SNAPSHOT,
    ANDROID,
    IOS
}

open class UpdateVersion : DefaultTask() {
    var versionType: VersionType? = null

    private data class VersionInfo(
        val version: Version,
        val androidVersionCode: Int,
        val iosVersionCode: Int
    ) {
        //there's no way to remove version metadata
        private fun Version.removeSnapshot(): Version {
            return Version.valueOf(toString().removeSuffix("-SNAPSHOT"))
        }

        fun incVersion(versionType: VersionType): VersionInfo {
            val isSnapshot = version.preReleaseVersion == "SNAPSHOT"

            //releases are from -SNAPSHOTs, patches from releases
            val nextVersion = when (versionType) {
                //0.n.x -> 0.n.(x + 1)
                VersionType.PATCH -> {
                    if (isSnapshot)
                        throw IllegalStateException("Cannot increase patch number on a SNAPSHOT version")

                    version.incrementPatchVersion()
                }

                //0.n.0-SNAPSHOT -> 0.n.0
                VersionType.MINOR -> {
                    if (!isSnapshot)
                        throw IllegalStateException("Cannot update minor number on a non-SNAPSHOT")

                    version.removeSnapshot()
                }

                //0.n.0 -> 0.(n + 1).0-SNAPSHOT
                VersionType.SNAPSHOT -> {
                    if (isSnapshot)
                        throw IllegalStateException("Cannot create next snapshot based on an existing SNAPSHOT version")

                    version.incrementMinorVersion().setPreReleaseVersion("SNAPSHOT")
                }

                VersionType.ANDROID -> {
                    return incAndroid()
                }

                VersionType.IOS -> {
                    return incIOS()
                }
            }

            val (nextAndroidVersionCode, nextIOSVersionCode) = if (versionType == VersionType.SNAPSHOT)
                androidVersionCode to iosVersionCode
            else
                nextAndroidVersion() to nextIOSVersion()

            return VersionInfo(nextVersion, nextAndroidVersionCode, nextIOSVersionCode)
        }

        private fun nextIOSVersion(): Int = iosVersionCode + 1

        private fun nextAndroidVersion(): Int = androidVersionCode + 1

        fun incIOS(): VersionInfo = copy(iosVersionCode = nextIOSVersion())

        fun incAndroid(): VersionInfo = copy(androidVersionCode = nextAndroidVersion())
    }

    private val gradlePropertiesPath = File(project.rootDir, "gradle.properties")

    private fun readVersionInfo(): VersionInfo {
        val props = Properties()

        gradlePropertiesPath.inputStream().use {
            props.load(it)
        }

        val versionProp = props.getProperty("VERSION") ?: error("VERSION missing from gradle.properties")
        val androidVersionProp = props.getProperty("ANDROID_VERSION_CODE") ?: error("ANDROID_VERSION_CODE missing from gradle.properties")
        val iosVersionProp = props.getProperty("IOS_VERSION_CODE") ?: error("IOS_VERSION_CODE missing from gradle.properties")

        return VersionInfo(
            Version.valueOf(versionProp),
            androidVersionProp.toInt(),
            iosVersionProp.toInt()
        )
    }

    private fun writeVersionInfo(newVersionInfo: VersionInfo) {
        //Properties.store always writes an annoying timestamp, so just do this
        gradlePropertiesPath.writer().use { writer ->
            writer.write("VERSION=${newVersionInfo.version}\n")
            writer.write("ANDROID_VERSION_CODE=${newVersionInfo.androidVersionCode}\n")
            writer.write("IOS_VERSION_CODE=${newVersionInfo.iosVersionCode}\n")
        }
    }

    private fun runCommand(command: List<String>) {
        logger.debug("Running {}", command)

        val process = ProcessBuilder(command).start()
        val r = process.waitFor()
        if (r != 0) {
            val stderr = process.errorStream.reader().use { it.readText() }
            throw RuntimeException("${command.joinToString(separator = " ")} failed:\n$stderr")
        }
    }

    private fun gitCommit(files: List<File>, commitMessage: String) {
        val command = mutableListOf(
            "git",
            "commit",
            "-m",
            commitMessage
        )

        files.forEach { command.add(it.toString()) }

        logger.info("Committing changes")

        runCommand(command)
    }

    private fun gitUndoChanges(files: List<File>) {
        if (files.isEmpty())
            return

        val command = mutableListOf("git", "checkout")
        command.addAll(files.map(File::toString))
        runCommand(command)
    }

    @TaskAction
    fun run() {
        val versionType = this.versionType ?: throw InvalidUserDataException("No versionType provided")

        val versionInfo = readVersionInfo()

        val nextVersionInfo = versionInfo.incVersion(versionType)

        val modifiedFiles = listOf(gradlePropertiesPath)

        val commitMessage = when (versionType) {
            VersionType.PATCH, VersionType.MINOR, VersionType.SNAPSHOT -> nextVersionInfo.version.toString()
            VersionType.ANDROID -> "[android] Version ${nextVersionInfo.androidVersionCode}"
            VersionType.IOS -> "[ios] Version ${nextVersionInfo.iosVersionCode}"
        }

        try {
            writeVersionInfo(nextVersionInfo)

            gitCommit(modifiedFiles, commitMessage)
        }
        catch (e: Exception) {
            gitUndoChanges(modifiedFiles)
            throw e
        }
    }
}
