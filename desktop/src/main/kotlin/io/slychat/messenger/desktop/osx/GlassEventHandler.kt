package io.slychat.messenger.desktop.osx

import com.sun.glass.ui.Application
import javafx.application.Platform

class GlassEventHandler(
    private val underlying: Application.EventHandler
) : Application.EventHandler() {
    override fun handleDidFinishLaunchingAction(app: Application, time: Long) {
        underlying.handleDidFinishLaunchingAction(app, time)
    }

    override fun handleDidBecomeActiveAction(app: Application, time: Long) {
        underlying.handleDidBecomeActiveAction(app, time)
    }

    override fun handleDidHideAction(app: Application, time: Long) {
        underlying.handleDidHideAction(app, time)
    }

    override fun handleDidReceiveMemoryWarning(app: Application, time: Long) {
        underlying.handleDidReceiveMemoryWarning(app, time)
    }

    override fun handleDidResignActiveAction(app: Application, time: Long) {
        underlying.handleDidResignActiveAction(app, time)
    }

    override fun handleDidUnhideAction(app: Application, time: Long) {
        underlying.handleDidUnhideAction(app, time)
    }

    override fun handleOpenFilesAction(app: Application, time: Long, files: Array<out String>) {
        underlying.handleOpenFilesAction(app, time, files)
    }

    //default handler just closes all windows, which is equiv to exit usually, but we have implicitExit off, so we need
    //to exit manually
    override fun handleQuitAction(app: Application, time: Long) {
        underlying.handleQuitAction(app, time)
        Platform.exit()
    }

    override fun handleThemeChanged(themeName: String): Boolean {
        return underlying.handleThemeChanged(themeName)
    }

    override fun handleWillBecomeActiveAction(app: Application, time: Long) {
        underlying.handleWillBecomeActiveAction(app, time)
    }

    override fun handleWillFinishLaunchingAction(app: Application, time: Long) {
        underlying.handleWillFinishLaunchingAction(app, time)
    }

    override fun handleWillHideAction(app: Application, time: Long) {
        underlying.handleWillHideAction(app, time)
    }

    override fun handleWillResignActiveAction(app: Application, time: Long) {
        underlying.handleWillResignActiveAction(app, time)
    }

    override fun handleWillUnhideAction(app: Application, time: Long) {
        underlying.handleWillUnhideAction(app, time)
    }
}
