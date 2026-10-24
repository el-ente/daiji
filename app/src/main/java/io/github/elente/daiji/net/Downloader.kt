package io.github.elente.daiji.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Downloader {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** Returns false when the server has no such file. Throws on any other failure. */
    fun download(url: String, dest: File, onProgress: (read: Long, total: Long) -> Unit = { _, _ -> }): Boolean {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode} for $url")
            writeAtomically(connection, dest, onProgress)
            return true
        } finally {
            connection.disconnect()
        }
    }

    private fun writeAtomically(connection: HttpURLConnection, dest: File, onProgress: (Long, Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val partial = File(dest.path + ".part")
        val total = connection.contentLengthLong
        var read = 0L
        connection.inputStream.use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    read += count
                    onProgress(read, total)
                }
            }
        }
        if (!partial.renameTo(dest)) {
            dest.delete()
            if (!partial.renameTo(dest)) throw IOException("Cannot move ${partial.name} to ${dest.name}")
        }
    }
}
