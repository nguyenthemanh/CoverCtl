package app.coverctl

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Build
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicReference

/**
 * ADB không dây độc lập (Dadb → adbd). Không dùng Shizuku.
 * Android 11+: cần ghép đôi Wireless debugging trước (mã 6 số) bằng công cụ hỗ trợ
 * cùng key — CoverCtl lưu key riêng; lần đầu bấm Kết nối có thể hiện «Allow».
 */
object AdbShell {
    private val clientRef = AtomicReference<Dadb?>(null)

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var endpoint: String = ""
        private set

    fun available(): Boolean = clientRef.get() != null

    fun statusLine(): String =
        if (available()) "ADB: đã kết nối $endpoint"
        else if (lastError.isNotBlank()) "ADB: chưa kết nối — $lastError"
        else "ADB: chưa kết nối (Wireless debugging)"

    fun disconnect() {
        runCatching { clientRef.getAndSet(null)?.close() }
        endpoint = ""
    }

    fun pair(host: String, pairPort: Int, code: String): String {
        // Dadb chưa có SPAKE2 pair built-in — hướng dẫn + lưu cổng
        lastError = "Cần ghép đôi TLS (Wireless debugging). Mở Dev options → Pair device, " +
            "hoặc dùng PC: adb pair $host:$pairPort $code rồi quay lại Kết nối."
        return "Ghép đôi: nhập mã trên máy/PC trước.\n" +
            "adb pair ${host.trim()}:$pairPort $code\n" +
            "Sau đó điền cổng kết nối (IP:port) và bấm Kết nối ADB.\n" +
            "(Kênh ADB độc lập — không dùng Shizuku.)"
    }

    fun connect(context: Context, host: String, port: Int): String {
        disconnect()
        return try {
            val keyPair = ensureKeyPair(context)
            val dadb = Dadb.create(host.trim(), port, keyPair)
            val probe = dadb.shell("echo coverctl-adb-ok")
            val out = probe.allOutput.trim()
            if (!out.contains("coverctl-adb-ok")) {
                runCatching { dadb.close() }
                lastError = "probe fail: $out"
                return "Kết nối ADB lỗi: $lastError"
            }
            clientRef.set(dadb)
            endpoint = "${host.trim()}:$port"
            lastError = ""
            "ADB đã kết nối $endpoint (độc lập, không Shizuku)"
        } catch (t: Throwable) {
            lastError = t.message ?: t.toString()
            "Kết nối ADB lỗi: $lastError\n" +
                "Bật Wireless debugging, ghép đôi (pair) rồi điền đúng cổng kết nối."
        }
    }

    fun exec(command: String): Shell.Result {
        val dadb = clientRef.get()
            ?: return Shell.Result(-1, "", "ADB chưa kết nối — chọn chế độ ADB rồi Kết nối")
        return try {
            val response = dadb.shell(command)
            Shell.Result(response.exitCode, response.allOutput.trim(), "")
        } catch (t: Throwable) {
            lastError = t.message ?: t.toString()
            disconnect()
            Shell.Result(-1, "", "ADB lỗi: $lastError")
        }
    }

    private fun ensureKeyPair(context: Context): AdbKeyPair {
        val dir = File(context.filesDir, "adb")
        if (!dir.exists()) dir.mkdirs()
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) {
            AdbKeyPair.generate(priv, pub)
        }
        return AdbKeyPair.read(priv, pub)
    }

    fun suggestedHost(context: Context): String {
        deviceIpv4(context)?.let { return it }
        return "127.0.0.1"
    }

    @Suppress("DEPRECATION")
    private fun deviceIpv4(context: Context): String? {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork ?: return null
                val lp: LinkProperties = cm.getLinkProperties(network) ?: return null
                for (addr in lp.linkAddresses) {
                    val a = addr.address
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        return a.hostAddress
                    }
                }
            }
        } catch (_: Throwable) {
        }
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        } catch (_: Throwable) {
        }
        return null
    }
}
