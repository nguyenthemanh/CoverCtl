package app.coverctl

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader

/**
 * Shell qua Shizuku — độc lập, không đụng ADB không dây.
 */
object ShizukuShell {
    fun available(): Boolean =
        runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun statusLine(): String =
        when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) ->
                "Shizuku: chưa chạy (mở app Shizuku → Start)"
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                "Shizuku: đã chạy — chưa cấp quyền CoverCtl"
            else -> "Shizuku: sẵn sàng"
        }

    fun exec(command: String): Shell.Result {
        if (!available()) {
            return Shell.Result(-1, "", "Shizuku chưa sẵn sàng / chưa cấp quyền")
        }
        return try {
            val process = newProcess(arrayOf("sh", "-c", command))
            val out = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val err = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val code = process.waitFor()
            Shell.Result(code, out.trim(), err.trim())
        } catch (t: Throwable) {
            Shell.Result(-1, "", t.message ?: t.toString())
        }
    }

    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, cmd, null, null) as Process
    }
}
