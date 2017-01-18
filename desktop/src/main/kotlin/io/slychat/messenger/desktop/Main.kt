package io.slychat.messenger.desktop

import ca.weblite.objc.RuntimeUtils.sel
import io.slychat.messenger.core.Os
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.desktop.osx.StartupNotificationObserver
import io.slychat.messenger.desktop.osx.ns.NSApplication
import io.slychat.messenger.desktop.osx.ns.NSNotificationCenter
import javafx.application.Application

//if we use an Application-derived class as our main class, java will actually launch our main via a proxy class, which
//launches the jfx toolkit in tandem with our main class (for preloader/etc support)
//on osx, we need to hook the NSApplicationDidFinishLaunchingNotification, so we don't want this behavior, since this
//creates a race condition attempting to register our NSNotification listener
class Main {
    companion object {
        var startupNotificationObserver: StartupNotificationObserver? = null
            private set

        @JvmStatic
        fun main(args: Array<String>) {
            if (currentOs.type.isPosix) {
                val libc = io.slychat.messenger.desktop.jna.CLibrary.INSTANCE
                //077, kotlin doesn't support octal literals
                libc.umask(63)
            }

            if (currentOs.type == Os.Type.OSX) {
                val observer = StartupNotificationObserver()
                startupNotificationObserver = observer
                val center = NSNotificationCenter.defaultCenter
                center.addObserver(observer, sel("handleNotification:"), NSApplication.DidFinishLaunchingNotification, null)
            }

            Application.launch(DesktopApp::class.java, *args)
        }
    }
}