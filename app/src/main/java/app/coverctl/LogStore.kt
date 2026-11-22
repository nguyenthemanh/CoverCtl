package app.coverctl

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private val lock = Any()
    private val buf = StringBuilder()

    fun append(title: String, body: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        synchronized(lock) {
            buf.append("[$ts] $title\n")
            if (body.isNotBlank()) buf.append(body.trim()).append('\n')
            buf.append("----\n")
            if (buf.length > 200_000) {
                buf.delete(0, buf.length - 150_000)
            }
        }
    }

    fun snapshot(): String = synchronized(lock) { buf.toString() }

    fun clear() = synchronized(lock) { buf.clear() }

    fun export(context: Context, extra: String = ""): Result {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val name = "CoverCtl-log-$stamp.txt"
        val content = buildString {
            appendLine("CoverCtl log export")
            appendLine("time=$stamp")
            appendLine("model=${Build.MODEL} device=${Build.DEVICE} android=${Build.VERSION.RELEASE}")
            appendLine(extra.trim())
            appendLine("==== session ====")
            append(snapshot().ifBlank { "(log trống — chưa chạy lệnh nào)" })
        }
        return try {
            // Luôn ghi FileProvider (chia sẻ ổn định). Downloads là phụ.
            val fileUri = writeToAppFiles(context, name, content)
            val downloadUri = runCatching { writeToDownloads(context, name, content) }.getOrNull()
            val where = if (downloadUri != null) {
                "Download/CoverCtl/$name"
            } else {
                "Android/data/${context.packageName}/files/Documents/$name"
            }
            Result(true, fileUri, downloadUri, name, "Đã lưu $where")
        } catch (t: Throwable) {
            Result(false, null, null, name, t.message ?: t.toString())
        }
    }

    fun shareIntent(context: Context, uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CoverCtl log")
            putExtra(Intent.EXTRA_TEXT, "CoverCtl log")
            clipData = ClipData.newUri(context.contentResolver, "CoverCtl log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.btn_share_log)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeToDownloads(context: Context, name: String, content: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CoverCtl")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: run {
                    resolver.delete(uri, null, null)
                    return null
                }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun writeToAppFiles(context: Context, name: String, content: String): Uri {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    data class Result(
        val ok: Boolean,
        val shareUri: Uri?,
        val downloadUri: Uri?,
        val fileName: String,
        val message: String
    )
}
