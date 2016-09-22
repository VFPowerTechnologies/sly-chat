package io.slychat.messenger.desktop

import javafx.scene.media.AudioClip

interface AudioPlayback {
    fun play(audioClip: AudioClip)
}