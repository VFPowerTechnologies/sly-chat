package io.slychat.messenger.desktop

import javafx.scene.media.AudioClip

class JfxAudioPlayback : AudioPlayback {
    override fun play(audioClip: AudioClip) {
        audioClip.play()
    }
}