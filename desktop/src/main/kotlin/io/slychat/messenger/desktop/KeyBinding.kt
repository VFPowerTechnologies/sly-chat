package io.slychat.messenger.desktop

import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyEvent

class KeyBinding(
    val combination: KeyCodeCombination,
    val action: () -> Unit
) {
    fun matches(event: KeyEvent): Boolean = combination.match(event)

    override fun toString(): String {
        return "KeyBinding($combination)"
    }
}