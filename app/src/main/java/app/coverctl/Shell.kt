package app.coverctl

/**
 * Điều phối shell theo chế độ đang chọn.
 * ADB và Shizuku độc lập — không fallback lẫn nhau.
 */
object Shell {
    @Volatile
    var mode: PrivilegeMode = PrivilegeMode.SHIZUKU

    fun available(): Boolean =
        when (mode) {
            PrivilegeMode.SHIZUKU -> ShizukuShell.available()
            PrivilegeMode.ADB -> AdbShell.available()
        }

    fun statusLine(): String =
        when (mode) {
            PrivilegeMode.SHIZUKU -> ShizukuShell.statusLine()
            PrivilegeMode.ADB -> AdbShell.statusLine()
        }

    fun exec(command: String): Result =
        when (mode) {
            PrivilegeMode.SHIZUKU -> {
                if (!ShizukuShell.available()) {
                    Result(-1, "", "Chế độ Shizuku — chưa sẵn sàng (không dùng ADB)")
                } else {
                    ShizukuShell.exec(command)
                }
            }
            PrivilegeMode.ADB -> {
                if (!AdbShell.available()) {
                    Result(-1, "", "Chế độ ADB — chưa kết nối (không dùng Shizuku)")
                } else {
                    AdbShell.exec(command)
                }
            }
        }

    data class Result(val code: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = code == 0
        fun text(): String =
            listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "ok ($code)" }
    }
}
