package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import java.io.InputStream

//TODO convert to FilterInputStream
class DecryptInputStream(
    private val cipher: Cipher,
    private val key: Key,
    private val inputStream: InputStream,
    chunkSize: Int
) : InputStream() {
    private enum class State {
        //if EOF is reached and no part of the iv+data has been read, move to EOF; if EOF is reached but some of the data has been read, move to copy_data
        READ_DATA,
        //once empty, move back to READ_DATA
        COPY_DATA,
        //terminate with read count if > 0, else return -1
        EOF
    }

    private var currentState = State.READ_DATA

    //this includes the IV
    private var encryptedChunkSize = cipher.getEncryptedSize(chunkSize)
    private var encryptedChunkReadSize = 0
    private var encryptedChunk = ByteArray(encryptedChunkSize)

    private var decryptedChunkSize = 0
    private var decryptedChunk: ByteArray? = null
    //how much has been copied to user buffer
    private var copiedDecryptedChunkSize = 0

    private class Update(val nextState: State, val hasMoreInput: Boolean, val read: Int)

    private fun handleReadData(): Update {
        val remaining = encryptedChunkSize - encryptedChunkReadSize

        val read = inputStream.read(encryptedChunk, encryptedChunkReadSize, remaining)

        if (read == -1) {
            if (encryptedChunkReadSize == 0)
                return Update(State.EOF, false, 0)
            else
                return Update(State.COPY_DATA, true, 0)
        }
        else if (read == 0)
            return Update(State.READ_DATA, false, 0)

        val hasMoreInput = read == remaining

        encryptedChunkReadSize += read

        return if (encryptedChunkReadSize == encryptedChunkSize)
            Update(State.COPY_DATA, true, 0)
        else
            Update(State.READ_DATA, hasMoreInput, 0)
    }

    private fun handleCopyData(b: ByteArray, off: Int, len: Int): Update {
        val remaining = decryptedChunkSize - copiedDecryptedChunkSize
        val toCopy = Math.min(len, remaining)

        System.arraycopy(
            decryptedChunk,
            copiedDecryptedChunkSize,
            b,
            off,
            toCopy
        )

        copiedDecryptedChunkSize += toCopy

        if (copiedDecryptedChunkSize == decryptedChunkSize)
            return Update(State.READ_DATA, true, toCopy)
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
        if (prev == State.READ_DATA && next == State.COPY_DATA) {
            copiedDecryptedChunkSize = 0

            val plaintext = cipher.decrypt(key, encryptedChunk, encryptedChunkReadSize)

            decryptedChunk = plaintext
            decryptedChunkSize = plaintext.size
        }
        else if (prev == State.COPY_DATA && next == State.READ_DATA) {
            encryptedChunkReadSize = 0
            decryptedChunkSize = 0
        }
    }

    override fun close() {
        inputStream.close()
    }
}