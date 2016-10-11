package io.slychat.messenger.desktop.services

import javafx.application.HostServices
import org.slf4j.LoggerFactory
import java.io.File

interface Browser {
    fun openUrl(url: String)
}

//xdg-open can open any type of file, so we can use it as a replacement for the missing HostServices.openDocument
class LinuxBrowser() : Browser {
    private val log = LoggerFactory.getLogger(javaClass)

    fun isPresentInPath(command: String): Boolean {
        val path = System.getenv("PATH")

        path.split(File.pathSeparator).forEach { dir ->
            val exe = File(dir, command)
            if (exe.canExecute())
                return true
        }

        return false
    }

    private var isXdgOpenAvailable: Boolean

    init {
        isXdgOpenAvailable = isPresentInPath("xdg-open")

        if (!isXdgOpenAvailable)
            log.warn("xdg-open not found, unable to open URLs")
    }

    override fun openUrl(url: String) {
        if (!isXdgOpenAvailable)
            return

        try {
            val process = ProcessBuilder("xdg-open", url).start()

            process.errorStream.close()
            process.inputStream.close()
            process.outputStream.close()
        }
        catch (t: Throwable) {
            log.warn("Unable to open url: {}", t.message, t)
        }
    }

}

class JFXBrowser(private val hostServices: HostServices) : Browser {
    override fun openUrl(url: String) {
        hostServices.showDocument(url)
    }
}

class DummyBrowser : Browser {
    override fun openUrl(url: String) {}
}
