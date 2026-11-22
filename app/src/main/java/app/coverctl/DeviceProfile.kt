package app.coverctl

import android.content.Context

/**
 * Preset Flip/Fold = đổi nhận dạng hệ thống (model/device props) + chỉnh màn ngoài.
 * Đổi ro.* cần Magisk resetprop (root). Không root thì chỉ áp được DPI/size + báo lỗi nhận dạng.
 */
enum class DeviceProfile(
    val id: String,
    val labelVi: String,
    val spoofModel: String?,
    val spoofDevice: String?,
    val spoofName: String?,
    val coverDisplay: Int,
    val dualState: Int,
    val density: Int,
    val size: Pair<Int, Int>?,
    val modelHints: List<String>
) {
    AUTO(
        "auto", "Tự nhận diện (không spoof)",
        null, null, null,
        1, 4, 300, null, emptyList()
    ),
    FLIP5(
        "flip5", "Nhận dạng: Galaxy Z Flip 5",
        "SM-F731B", "b5q", "b5qxxx",
        1, 4, 300, 720 to 748,
        listOf("SM-F731", "SCG23", "SCG24")
    ),
    FLIP6(
        "flip6", "Nhận dạng: Galaxy Z Flip 6",
        "SM-F741B", "b6q", "b6qxxx",
        1, 4, 320, 720 to 748,
        listOf("SM-F741")
    ),
    FLIP7(
        "flip7", "Nhận dạng: Galaxy Z Flip 7",
        "SM-F766B", "b7q", "b7qxxx",
        1, 4, 340, 1080 to 1008,
        listOf("SM-F766", "SM-F761")
    ),
    // Fold = chế độ MÀN MỞ (full). Không ép 904x2316 lên cover Flip (gây cửa sổ dọc hẹp).
    // size=null → luôn dùng physical cover cho full màn.
    FOLD5(
        "fold5", "Fold 5 — màn mở (full cover)",
        "SM-F946B", "q5q", "q5qxxx",
        1, 4, 300, null,
        listOf("SM-F946", "SM-F9")
    ),
    FOLD6(
        "fold6", "Fold 6 — màn mở (full cover)",
        "SM-F956B", "q6q", "q6qxxx",
        1, 4, 300, null,
        listOf("SM-F956")
    );

    companion object {
        fun fromId(id: String): DeviceProfile =
            entries.firstOrNull { it.id == id } ?: AUTO

        fun detectFromModel(model: String): DeviceProfile {
            val m = model.uppercase()
            return entries.filter { it != AUTO }
                .firstOrNull { p -> p.modelHints.any { m.contains(it.uppercase()) } }
                ?: FLIP5
        }

        fun uiEntries(): List<DeviceProfile> = entries.toList()
    }

    fun label(context: Context): String = when (this) {
        AUTO -> context.getString(R.string.profile_auto)
        FLIP5 -> context.getString(R.string.profile_flip5)
        FLIP6 -> context.getString(R.string.profile_flip6)
        FLIP7 -> context.getString(R.string.profile_flip7)
        FOLD5 -> context.getString(R.string.profile_fold5)
        FOLD6 -> context.getString(R.string.profile_fold6)
    }
}
