package dev.hnm.workbench.android
import java.io.ByteArrayOutputStream
import java.io.InputStream
/** API 31-compatible bounded read, including one overflow byte. */
fun InputStream.readBounded(limit: Int): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (result.size() <= limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit + 1 - result.size()))
        if (count < 0) break
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}
