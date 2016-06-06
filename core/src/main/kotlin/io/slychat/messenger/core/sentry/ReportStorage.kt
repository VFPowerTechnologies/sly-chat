package io.slychat.messenger.core.sentry

import java.io.*
import java.util.*

/** Don't throw exceptions from this. */
interface ReportStorage<ReportType> {
    fun store(reports: Collection<ReportType>)
    fun get(): Collection<ReportType>
}

/** a ReportStorage implementation that does nothing. */
class DummyReportStorage : ReportStorage<ByteArray> {
    override fun store(reports: Collection<ByteArray>) {
    }

    override fun get(): Collection<ByteArray> {
        return emptyList()
    }
}

/** A ReportStorage implementation that writes reports out to the given file. */
class FileReportStorage(private val path: File) : ReportStorage<ByteArray> {
    override fun store(reports: Collection<ByteArray>) {
        if (reports.isEmpty()) {
            path.delete()
        }
        else {
            path.outputStream().use {
                DataOutputStream(it).use { outputStream ->
                    reports.forEach { report ->
                        outputStream.writeInt(report.size)
                        outputStream.write(report)
                    }
                }
            }
        }
    }

    override fun get(): Collection<ByteArray> {
        val r = ArrayList<ByteArray>()

        try {
            path.inputStream().use {
                DataInputStream(it).use { inputStream ->
                    while (true) {
                        try {
                            val size = inputStream.readInt()
                            val report = ByteArray(size)
                            inputStream.read(report)
                            r.add(report)
                        }
                        catch (e: EOFException) {
                            break
                        }
                    }
                }
            }
        }
        catch (e: FileNotFoundException) {
            return r
        }

        return r
    }
}

