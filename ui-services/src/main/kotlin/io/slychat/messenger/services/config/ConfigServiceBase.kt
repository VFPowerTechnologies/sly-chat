package io.slychat.messenger.services.config

import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject

abstract class ConfigServiceBase<ConfigT : Any, out EditorT : ConfigServiceBase.EditorInterface<ConfigT>>(
    protected val backend: ConfigBackend
) {
    interface EditorInterface<out ConfigT> {
        val modifiedKeys: Set<String>
        val config: ConfigT
    }

    protected abstract var config: ConfigT

    protected abstract val configClass: Class<ConfigT>

    private val subject = PublishSubject.create<Collection<String>>()
    val updates: Observable<Collection<String>>
        get() = subject

    fun init(): Promise<Unit, Exception> {
        return backend.read(configClass) mapUi { newConfig ->
            if (newConfig != null)
                config = newConfig
        }
    }


    protected fun emitChange(keys: Collection<String>) {
        subject.onNext(keys)
    }

    protected fun emitChange(key: String) {
        emitChange(listOf(key))
    }

    protected abstract fun makeEditor(): EditorT

    private fun applyModifications(editorInterface: EditorInterface<ConfigT>) {
        val modifiedKeys = editorInterface.modifiedKeys

        if (modifiedKeys.isEmpty())
            return

        val newConfig = editorInterface.config
        if (newConfig != config) {
            config = newConfig
            backend.update(newConfig)
        }

        emitChange(modifiedKeys)
    }

    fun <R> withEditor(body: (EditorT.() -> R)): R {
        val editor = makeEditor()

        val r = editor.body()

        applyModifications(editor)

        return r
    }
}
