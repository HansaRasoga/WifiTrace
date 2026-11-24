package com.group12.wifitrace

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Trial(
    val timestamp: String,
    val label: String,
    val number: Int,
    val readings: List<Int>,
    val average: Double
)

/**
 * Reads and writes wifi_logs.csv.
 * Columns: timestamp,label,trial,R1..R10,average
 * No SSID / BSSID / MAC address is ever stored.
 */
object LogRepository {

    const val FILE_NAME = "wifi_logs.csv"
    private const val N = Plan.READINGS_PER_TRIAL

    fun file(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    private fun header(): String {
        val sb = StringBuilder("timestamp,label,trial")
        for (i in 1..N) sb.append(",R").append(i)
        sb.append(",average")
        return sb.toString()
    }

    private fun dataLines(f: File): List<String> {
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() && !it.startsWith("timestamp") }
    }

    private fun parse(line: String): Trial? = try {
        val p = line.split(",")
        if (p.size < 3 + N + 1) {
            null
        } else {
            Trial(
                timestamp = p[0],
                label = p[1],
                number = p[2].trim().toInt(),
                readings = p.subList(3, 3 + N).map { it.trim().toInt() },
                average = p[3 + N].trim().toDouble()
            )
        }
    } catch (e: Exception) {
        null
    }

    fun readAll(context: Context): List<Trial> =
        dataLines(file(context)).mapNotNull { parse(it) }

    /** Appends one trial and returns it (with its automatic trial number for that label). */
    fun append(context: Context, label: String, readings: List<Int>): Trial {
        val clean = label.trim().replace(",", "_")
        val number = readAll(context).count { it.label == clean } + 1
        val average = String.format(Locale.US, "%.1f", readings.average()).toDouble()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        val f = file(context)
        val isNew = !f.exists() || f.length() == 0L

        val sb = StringBuilder()
        if (isNew) sb.append(header()).append('\n')
        sb.append(timestamp).append(',').append(clean).append(',').append(number)
        for (v in readings) sb.append(',').append(v)
        sb.append(',').append(String.format(Locale.US, "%.1f", average)).append('\n')

        FileOutputStream(f, true).use { it.write(sb.toString().toByteArray()) }
        return Trial(timestamp, clean, number, readings, average)
    }

    /** Removes the most recent row. Returns false if there was nothing to remove. */
    fun deleteLast(context: Context): Boolean {
        val f = file(context)
        val data = dataLines(f)
        if (data.isEmpty()) return false
        val kept = data.dropLast(1)
        val text = StringBuilder(header()).append('\n')
        kept.forEach { text.append(it).append('\n') }
        f.writeText(text.toString())
        return true
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Copies the CSV into the public Downloads folder. Returns the new file name. */
    fun exportToDownloads(context: Context): String {
        val f = file(context)
        if (!f.exists()) throw IllegalStateException("No log yet")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "wifi_logs_$stamp.csv"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            f.copyTo(File(dir, name), overwrite = true)
        }
        return name
    }
}
