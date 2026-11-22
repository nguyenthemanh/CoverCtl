package app.coverctl

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Chế độ màn theo flip-ctl: Main / Cover / Dual.
 * State id lấy theo tên từ `cmd device_state print-states`, không hard-code.
 */
object ScreenModes {
    const val COVER_DISPLAY = 1

    enum class Mode(val id: String) {
        MAIN("main"),
        COVER("cover"),
        DUAL("dual");

        companion object {
            fun fromId(id: String?): Mode =
                entries.firstOrNull { it.id == id } ?: MAIN
        }
    }

    data class StateInfo(val id: Int, val name: String)

    fun listStates(): List<StateInfo> {
        val out = Shell.exec("cmd device_state print-states 2>&1").text()
        val re = Regex("""identifier=(\d+),\s*name='([^']+)'""")
        return re.findAll(out).map {
            StateInfo(it.groupValues[1].toInt(), it.groupValues[2])
        }.toList()
    }

    fun findState(vararg namePatterns: Regex): StateInfo? {
        val states = listStates()
        for (p in namePatterns) {
            states.firstOrNull { p.matches(it.name) }?.let { return it }
        }
        return null
    }

    fun foldedState(): StateInfo? {
        findState(Regex("^(FOLDED|CLOSED)$"))?.let { return it }
        return listStates().firstOrNull { s ->
            val n = s.name.uppercase()
            (n.contains("FOLD") || n.contains("CLOS")) &&
                !n.contains("HALF") &&
                !n.contains("OPEN") &&
                !n.contains("CONCURRENT") &&
                !n.contains("REAR") &&
                !n.contains("DUAL")
        }
    }

    fun dualState(): StateInfo? =
        findState(
            Regex("^CONCURRENT_INNER_DEFAULT$"),
            Regex("^CONCURRENT.*INNER.*$"),
            Regex("^CONCURRENT.*$")
        )

    fun currentStateId(): Int? {
        val text = Shell.exec("cmd device_state state 2>&1").text()
        val m = Regex("""Committed state: DeviceState\{identifier=(\d+)""").find(text)
            ?: Regex("""Override state: DeviceState\{identifier=(\d+)""").find(text)
            ?: Regex("""identifier=(\d+)""").find(text)
        return m?.groupValues?.get(1)?.toIntOrNull()
    }

    fun currentMode(): Mode {
        val states = listStates()
        val id = currentStateId() ?: return Mode.MAIN
        val name = states.firstOrNull { it.id == id }?.name.orEmpty()
        return when {
            name.startsWith("CONCURRENT") -> Mode.DUAL
            name == "FOLDED" || name == "CLOSED" ||
                (name.contains("FOLD", true) || name.contains("CLOS", true)) &&
                !name.contains("HALF", true) && !name.contains("OPEN", true) -> Mode.COVER
            else -> {
                val hasOverride = Shell.exec("cmd device_state state 2>&1")
                    .text().contains("Override state")
                if (hasOverride) Mode.COVER else Mode.MAIN
            }
        }
    }

    fun grantOverlay(context: Context): Shell.Result {
        val pkg = context.packageName
        return Shell.exec("appops set $pkg SYSTEM_ALERT_WINDOW allow; appops set $pkg POST_NOTIFICATION allow 2>/dev/null || true")
    }

    fun apply(
        context: Context,
        mode: Mode,
        keepDual: Boolean = Prefs.keepDual(context),
        profile: DeviceProfile = DeviceProfile.fromId(Prefs.profileId(context))
    ): Shell.Result {
        Shell.mode = Prefs.privilegeMode(context)
        Prefs.setScreenMode(context, mode.id)
        grantOverlay(context)

        return when (mode) {
            Mode.MAIN -> {
                DualKeepService.stop(context)
                CoverNavOverlayService.stop(context)
                Shell.exec(
                    "cmd device_state state reset >/dev/null 2>&1; " +
                        "cmd device_state base-state reset >/dev/null 2>&1; " +
                        "sleep 0.5; cmd device_state state; echo MODE=main"
                )
            }
            Mode.COVER -> {
                DualKeepService.stop(context)
                val folded = foldedState()
                    ?: return Shell.Result(1, "", "Không tìm thấy state CLOSED/FOLDED trên máy này")
                val r = Shell.exec(
                    "cmd device_state state ${folded.id}; sleep 0.8; " +
                        "cmd device_state state; echo MODE=cover state=${folded.id}"
                )
                CoverNavOverlayService.start(context, Mode.COVER.id)
                r
            }
            Mode.DUAL -> {
                val dual = dualState()
                    ?: return Shell.Result(1, "", "Không tìm thấy state CONCURRENT (dual) trên máy này")
                val coverFix = CoverCommands.fixCoverFullUi(profile)
                val r = Shell.exec(
                    "cmd device_state state ${dual.id}; sleep 0.5; " +
                        "settings put secure show_navigation_for_subscreen 1; " +
                        "cmd device_state state; echo MODE=dual state=${dual.id}"
                )
                CoverNavOverlayService.start(context, Mode.DUAL.id)
                if (keepDual) {
                    Prefs.setKeepDual(context, true)
                    DualKeepService.start(context, dual.id)
                } else {
                    DualKeepService.stop(context)
                }
                Shell.Result(
                    if (r.ok) 0 else r.code,
                    listOf(coverFix.text(), r.text()).joinToString("\n"),
                    ""
                )
            }
        }
    }

    fun launchOnCover(packageOrComponent: String): Shell.Result {
        val component = if (packageOrComponent.contains("/")) {
            packageOrComponent
        } else {
            val resolved = Shell.exec(
                "cmd package resolve-activity --brief -a android.intent.action.MAIN " +
                    "-c android.intent.category.LAUNCHER $packageOrComponent 2>/dev/null | tail -n 1"
            ).stdout.trim().lines().lastOrNull().orEmpty()
            if (!resolved.contains("/")) {
                return Shell.Result(1, "", "Không resolve được launcher cho $packageOrComponent")
            }
            resolved
        }
        return Shell.exec(
            "am start --display $COVER_DISPLAY --activity-clear-task -n $component 2>&1; " +
                "cmd activity display move-stack $COVER_DISPLAY 2>/dev/null || true"
        )
    }

    fun doctor(context: Context): String = buildString {
        if (foldedState() == null) appendLine("warn: không có state folded/closed → Cover mode khó hoạt động")
        if (dualState() == null) appendLine("warn: không có state concurrent → Dual mode khó hoạt động")
        val model = CoverCommands.readModel()
        if (!model.uppercase().contains("F731") && !model.uppercase().contains("SCG23") &&
            !model.uppercase().contains("F74") && !model.uppercase().contains("F76")
        ) {
            appendLine("warn: chưa test kỹ trên $model")
        }
        if (!Settings.canDrawOverlays(context)) {
            appendLine("warn: chưa cấp quyền hiển thị trên ứng dụng khác (overlay) — thanh điều hướng cover cần quyền này")
        }
        if (!Shell.available()) {
            appendLine("warn: privilege shell chưa sẵn sàng (${Prefs.privilegeMode(context).id})")
        }
        val usb = Shell.exec("dumpsys usb 2>/dev/null | grep -q 'screen_unlocked_functions=' && echo BAD_USB || echo USB_OK")
        if (usb.stdout.contains("BAD_USB")) {
            appendLine("warn: đặt USB mặc định = No data transfer (tránh Shizuku chết khi khóa máy)")
        }
        if (isBlank()) appendLine("ok: không cảnh báo doctor")
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
