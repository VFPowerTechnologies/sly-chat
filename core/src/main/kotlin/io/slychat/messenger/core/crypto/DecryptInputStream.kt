package io.slychat.messenger.core.crypto

import org.spongycastle.crypto.engines.AESFastEngine
import org.spongycastle.crypto.modes.GCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import java.io.InputStream

fun decryptBuffer(key: ByteArray, data: ByteArray, size: Int): ByteArray {
    val authTagLength = 128

    val cipher = GCMBlockCipher(AESFastEngine())

    val iv = ByteArray(96 / 8)
    System.arraycopy(
        data,
        0,
        iv,
        0,
        iv.size
    )

    val dataSize = size - iv.size

    cipher.init(false, AEADParameters(KeyParameter(key), authTagLength, iv))

    val plaintext = ByteArray(cipher.getOutputSize(dataSize))

    val outputLength = cipher.processBytes(data, iv.size, dataSize, plaintext, 0)
    cipher.doFinal(plaintext, outputLength)

    return plaintext
}

//TODO convert to FilterInputStream
class DecryptInputStream(
    private val key: ByteArray,
    private val inputStream: InputStream,
    val chunkSize: Int
) : InputStream() {
    private enum class State {
        //if EOF is reached and no part of the iv has been read, move to EOF; if EOF is reached but some of the iv's been reached, throw exception
        READ_IV,
        //if EOF is reached, move to copy_data
        READ_DATA,
        //once empty, move back to IV
        COPY_DATA,
        //terminate with read count if > 0, else return -1
        EOF
    }

    private var ivSize = 96 / 8
    private var ivReadSize = 0
    private var currentIV = ByteArray(ivSize)

    private var currentState = State.READ_IV

    private var encryptedChunkSize = 0
    private var encryptedChunkReadSize = 0

    private var chunkReadSize = 0
    private var currentChunk = ByteArray(chunkSize)
    //how much has been copied to user buffer
    private var copiedChunkSize = 0
    private var cipher: GCMBlockCipher? = null

    private class Update(val nextState: State, val hasMoreInput: Boolean, val read: Int)

    init {
        val cipher = GCMBlockCipher(AESFastEngine())
        val authTagLength = 128

        val iv = ByteArray(ivSize)
        cipher.init(true, AEADParameters(KeyParameter(key), authTagLength, iv))
        encryptedChunkSize = cipher.getOutputSize(chunkSize)
    }

    private fun initCipher() {
        val authTagLength = 128

        val cipher = GCMBlockCipher(AESFastEngine())

        cipher.init(false, AEADParameters(KeyParameter(key), authTagLength, currentIV))

        this.cipher = cipher
    }

    private fun handleReadIV(): Update {
        val remaining = ivSize - ivReadSize

        val read = inputStream.read(currentIV, ivReadSize, remaining)

        if (read == -1) {
            if (ivReadSize == 0)
                return Update(State.EOF, false, 0)
            else
                error("EOF while reading IV")
        }

        val hasMoreInput = read != 0

        ivReadSize += read

        return if (ivReadSize == ivSize)
            Update(State.READ_DATA, true, 0)
        else
            Update(State.READ_IV, hasMoreInput, 0)
    }

    private fun handleReadData(): Update {
        val cipher = this.cipher ?: error("No cipher set")

        val remaining = encryptedChunkSize - encryptedChunkReadSize

        //XXX do something about this so we don't alloc everytime
        val buffer = ByteArray(remaining)

        val read = inputStream.read(buffer, 0, remaining)

        if (read == -1)
            return Update(State.COPY_DATA, true, 0)
        else if (read == 0)
            return Update(State.READ_DATA, false, 0)

        val hasMoreInput = read == remaining

        encryptedChunkReadSize += read

        val n = cipher.processBytes(buffer, 0, read, currentChunk, chunkReadSize)
        chunkReadSize += n

        return if (encryptedChunkReadSize == encryptedChunkSize)
            Update(State.COPY_DATA, true, 0)
        else
            Update(State.READ_DATA, hasMoreInput, 0)
    }

    private fun handleCopyData(b: ByteArray, off: Int, len: Int): Update {
        val remaining = chunkReadSize - copiedChunkSize
        val toCopy = Math.min(len, remaining)

        System.arraycopy(
            currentChunk,
            copiedChunkSize,
            b,
            off,
            toCopy
        )

        copiedChunkSize += toCopy

        if (copiedChunkSize == chunkReadSize)
            return Update(State.READ_IV, true, toCopy)
        else
            return Update(State.COPY_DATA, true, toCopy)
    }

    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        var remaining = len
        var read = 0
        var offset = off

        loop@ while (remaining > 0) {
            val update = when (currentState) {
                State.READ_IV -> handleReadIV()
                State.READ_DATA -> handleReadData()
                State.COPY_DATA -> handleCopyData(b, offset, remaining)
                State.EOF -> break@loop
            }

            handleTransition(currentState, update.nextState)
            currentState = update.nextState

            offset += update.read
            remaining -= update.read
            read += update.read

            if (!update.hasMoreInput)
                break
        }

        return if (currentState == State.EOF && read == 0)
            -1
        else
            read
    }

    private fun handleTransition(prev: State, next: State) {
        if (prev == State.READ_IV && next == State.READ_DATA) {
            encryptedChunkReadSize = 0
            chunkReadSize = 0

            initCipher()
        }
        else if (prev == State.READ_DATA && next == State.COPY_DATA) {
            copiedChunkSize = 0

            val cipher = this.cipher ?: error("No cipher set")

            chunkReadSize += cipher.doFinal(currentChunk, chunkReadSize)
        }
        else if (prev == State.COPY_DATA && next == State.READ_IV) {
            ivReadSize = 0
        }
    }

    override fun close() {
        inputStream.close()
    }
}