package app.coverctl

object CoverCommands {

    fun resolveProfile(selected: DeviceProfile): DeviceProfile {
        if (selected != DeviceProfile.AUTO) return selected
        return DeviceProfile.detectFromModel(readModel())
    }

    fun readModel(): String {
        val r = Shell.exec("getprop ro.product.model")
        return r.stdout.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { "unknown" }
    }

    fun readIdentityBlock(): Shell.Result =
        Shell.exec(
            """
            echo MODEL=$(getprop ro.product.model)
            echo DEVICE=$(getprop ro.product.device)
            echo NAME=$(getprop ro.product.name)
            echo SYSTEM_MODEL=$(getprop ro.product.system.model)
            echo VENDOR_MODEL=$(getprop ro.product.vendor.model)
            echo BUILD_PRODUCT=$(getprop ro.build.product)
            echo ONEUI=$(getprop ro.build.version.oneui)
            echo ANDROID=$(getprop ro.build.version.release)
            echo FINGERPRINT=$(getprop ro.build.fingerprint)
            if command -v resetprop >/dev/null 2>&1; then echo ROOT_TOOL=resetprop
            elif su -c id >/dev/null 2>&1; then echo ROOT_TOOL=su
            else echo ROOT_TOOL=none
            fi
            """.trimIndent().lines().joinToString("; ")
        )

    fun ensureDual(profile: DeviceProfile): Shell.Result =
        Shell.exec("cmd device_state state ${profile.dualState}")

    /**
     * Dual + DPI. Fold = màn mở (full physical cover) — không bao giờ ép 904x2316
     * (đó là tỉ lệ màn đóng/cửa sổ dọc hẹp trên Flip).
     */
    fun applyDisplayProfile(selected: DeviceProfile): Shell.Result {
        val profile = resolveProfile(selected)
        val d = profile.coverDisplay
        val foldOpen = profile.id.startsWith("fold")
        val dpi = if (foldOpen) 300 else profile.density
        val sizeCmd = when {
            foldOpen || profile.size == null ->
                "wm size reset -d $d; " +
                    "PHYS=\$(wm size -d $d 2>/dev/null | grep -i 'Physical size' | sed 's/.*Physical size: *//'); " +
                    "if [ -n \"\$PHYS\" ]; then wm size \"\$PHYS\" -d $d; echo size_physical=\$PHYS; fi; "
            else -> {
                val (w, h) = profile.size
                "wm size ${w}x${h} -d $d; "
            }
        }
        val modeLabel = if (foldOpen) "Fold MÀN MỞ (full)" else profile.labelVi
        return Shell.exec(
            "echo '=== Áp kiểu màn: $modeLabel ==='; " +
                "cmd device_state state ${profile.dualState}; " +
                sizeCmd +
                "wm density $dpi -d $d; " +
                "echo 'OK dual=${profile.dualState} density=$dpi display=$d fold_open=$foldOpen'; " +
                "wm density -d $d; wm size -d $d; cmd device_state state"
        )
    }

    /**
     * Hiện lại thanh điều hướng / SystemUI trên màn phụ (cover).
     * Samsung hay để show_navigation_for_subscreen=0 → mất Back/Home/Recents.
     */
    fun restoreCoverNavigation(selected: DeviceProfile): Shell.Result {
        val profile = resolveProfile(selected)
        val d = profile.coverDisplay
        return Shell.exec(
            """
            echo '=== Khôi phục điều hướng màn ngoài ==='
            settings put secure show_navigation_for_subscreen 1
            settings put system large_cover_screen_navigation 1
            settings put system large_cover_screen_apps 1
            settings put global navigation_bar_gesture_while_hidden 0
            settings put secure navigation_mode 0
            settings delete global policy_control 2>/dev/null || true
            cmd overlay disable com.android.internal.systemui.navbar.transparent 2>/dev/null || true
            cmd overlay disable com.android.internal.systemui.navbar.gestural 2>/dev/null || true
            cmd overlay disable com.samsung.internal.systemui.navbar.sec_gestural 2>/dev/null || true
            cmd overlay disable com.samsung.internal.systemui.navbar.sec_gestural_no_hint 2>/dev/null || true
            cmd overlay disable com.samsung.internal.systemui.navbar.gestural_no_hint 2>/dev/null || true
            cmd overlay enable com.android.internal.systemui.navbar.threebutton
            cmd device_state state ${profile.dualState}
            killall com.android.systemui 2>/dev/null || true
            sleep 1.5
            am start --display $d --activity-clear-task -n com.sec.android.app.launcher/com.sec.android.app.launcher.activities.LauncherActivity 2>/dev/null || am start --display $d -a android.intent.action.MAIN -c android.intent.category.HOME
            echo show_navigation_for_subscreen=$(settings get secure show_navigation_for_subscreen)
            cmd overlay list | grep -E 'navbar' || true
            echo 'Xong: 3 nút điều hướng trên màn ngoài'
            """.trimIndent().lines().joinToString("; ")
        )
    }

    /**
     * Full cover / Fold màn mở: dual + physical size + DPI + nav + launcher.
     * Sửa cửa sổ dọc hẹp giữa màn ngoài.
     */
    fun fixCoverFullUi(selected: DeviceProfile): Shell.Result {
        val profile = resolveProfile(selected)
        val d = profile.coverDisplay
        val foldOpen = profile.id.startsWith("fold")
        val dpi = when {
            foldOpen -> 300
            profile.density in 260..360 -> profile.density
            else -> 300
        }
        return Shell.exec(
            """
            echo '=== Full màn ngoài / Fold màn MỞ ==='
            cmd device_state state 4
            settings put secure show_navigation_for_subscreen 1
            settings put system large_cover_screen_navigation 1
            settings put system large_cover_screen_apps 1
            settings put global navigation_bar_gesture_while_hidden 0
            settings put secure navigation_mode 0
            settings delete global policy_control 2>/dev/null || true
            cmd overlay disable com.android.internal.systemui.navbar.transparent 2>/dev/null || true
            cmd overlay disable com.android.internal.systemui.navbar.gestural 2>/dev/null || true
            cmd overlay disable com.samsung.internal.systemui.navbar.sec_gestural 2>/dev/null || true
            cmd overlay enable com.android.internal.systemui.navbar.threebutton
            echo 'TRƯỚC:'
            wm size -d $d
            wm density -d $d
            wm size reset -d $d 2>/dev/null || true
            sleep 0.2
            # Flip cover thật: 748x720 — ép rõ để hết logical 904x2244 (cửa sổ hẹp)
            wm size 748x720 -d $d 2>/dev/null || wm size reset -d $d
            sleep 0.2
            wm density $dpi -d $d 2>/dev/null || sleep 0.3; wm density $dpi -d $d
            echo 'SAU:'
            wm size -d $d
            wm density -d $d
            cmd device_state state
            killall com.android.systemui 2>/dev/null || true
            sleep 1.2
            am start --display $d --activity-clear-task -n com.sec.android.app.launcher/com.sec.android.app.launcher.activities.LauncherActivity 2>/dev/null || am start --display $d -a android.intent.action.MAIN -c android.intent.category.HOME
            echo 'Xong: size=748x720 density=$dpi + nav màn ngoài'
            """.trimIndent().lines().joinToString("; ")
        )
    }

    /**
     * Đổi nhận dạng hệ thống (ro.product.*) — cần Magisk resetprop.
     * Trả SPOOF_OK / SPOOF_FAIL rõ ràng; không làm fail cả lệnh display.
     */
    fun applyIdentitySpoof(selected: DeviceProfile): Shell.Result {
        val profile = resolveProfile(selected)
        val model = profile.spoofModel
            ?: return Shell.Result(
                0,
                "SPOOF_SKIP: Hồ sơ Tự nhận diện — không spoof.\nChọn Flip 5/6/7 hoặc Fold rồi thử lại.",
                ""
            )
        val device = profile.spoofDevice ?: model
        val name = profile.spoofName ?: device
        return Shell.exec(
            """
            echo '=== Đổi nhận dạng -> $model / $device ==='
            BEFORE=${'$'}(getprop ro.product.model)
            echo "Trước: MODEL=${'$'}BEFORE"
            SPOOF_OK=0
            METHOD=none
            for RP in resetprop /data/adb/magisk/resetprop /sbin/resetprop; do
              if [ -x "${'$'}RP" ] || command -v "${'$'}RP" >/dev/null 2>&1; then
                ${'$'}RP ro.product.model '$model' && ${'$'}RP ro.product.device '$device' && ${'$'}RP ro.product.name '$name' && ${'$'}RP ro.product.system.model '$model' && ${'$'}RP ro.product.vendor.model '$model' && ${'$'}RP ro.build.product '$device' && SPOOF_OK=1 && METHOD="${'$'}RP" && break
              fi
            done
            if [ "${'$'}SPOOF_OK" != 1 ] && su -c id >/dev/null 2>&1; then
              su -c "resetprop ro.product.model '$model'; resetprop ro.product.device '$device'; resetprop ro.product.name '$name'; resetprop ro.product.system.model '$model'; resetprop ro.product.vendor.model '$model'; resetprop ro.build.product '$device'" && SPOOF_OK=1 && METHOD=su_resetprop
            fi
            if [ "${'$'}SPOOF_OK" != 1 ]; then
              setprop ro.product.model '$model' 2>/dev/null || true
              AFTER_TRY=${'$'}(getprop ro.product.model)
              if [ "${'$'}AFTER_TRY" = '$model' ]; then SPOOF_OK=1; METHOD=setprop; fi
            fi
            AFTER=${'$'}(getprop ro.product.model)
            echo "Sau: MODEL=${'$'}AFTER DEVICE=${'$'}(getprop ro.product.device)"
            if [ "${'$'}SPOOF_OK" = 1 ] && [ "${'$'}AFTER" = '$model' ]; then
              echo "SPOOF_OK method=${'$'}METHOD"
              echo "Nhận dạng đã đổi. Một số app cần khởi động lại máy."
            else
              echo "SPOOF_FAIL"
              echo "LỖI: Không đổi được kiểu máy."
              echo "Máy chưa Magisk/root — thuộc tính ro.* chỉ đọc."
              echo "Cần Magisk + resetprop. Shizuku/ADB thường KHÔNG đủ."
              echo "Phần «Áp kiểu màn ngoài» vẫn dùng được (dual/DPI)."
              echo "Phần thông tin máy hiện MODEL thật — đúng, không phải lỗi UI."
            fi
            """.trimIndent().lines().joinToString("; ")
        )
    }

    fun applyProfile(selected: DeviceProfile): Shell.Result {
        val spoof = applyIdentitySpoof(selected)
        val display = applyDisplayProfile(selected)
        val text = buildString {
            appendLine(spoof.text())
            appendLine()
            appendLine(display.text())
        }
        val code = when {
            spoof.text().contains("SPOOF_FAIL") -> 2
            !display.ok -> display.code
            else -> 0
        }
        return Shell.Result(code, text, "")
    }

    fun setDensity(profile: DeviceProfile, dpi: Int): Shell.Result {
        val p = resolveProfile(profile)
        ensureDual(p)
        return Shell.exec("wm density $dpi -d ${p.coverDisplay}")
    }

    fun resetDensity(profile: DeviceProfile): Shell.Result {
        val p = resolveProfile(profile)
        ensureDual(p)
        val d = p.coverDisplay
        return Shell.exec("wm density reset -d $d; wm size reset -d $d")
    }

    fun getStatus(profile: DeviceProfile): Shell.Result {
        val p = resolveProfile(profile)
        val d = p.coverDisplay
        val target = p.spoofModel?.let { "target_model=$it target_device=${p.spoofDevice}" }
            ?: "target=auto(no-spoof)"
        return Shell.exec(
            "echo '=== Thông tin máy ==='; " +
                "echo model=$(getprop ro.product.model); " +
                "echo device=$(getprop ro.product.device); " +
                "echo name=$(getprop ro.product.name); " +
                "echo oneui=$(getprop ro.build.version.oneui); " +
                "echo android=$(getprop ro.build.version.release); " +
                "echo 'Hồ sơ CoverCtl: ${p.labelVi}'; " +
                "echo $target; " +
                "if command -v resetprop >/dev/null 2>&1 || su -c id >/dev/null 2>&1; then echo spoof_capable=yes; else echo spoof_capable=NO_ROOT; fi; " +
                "echo '=== Trạng thái màn ==='; cmd device_state state; " +
                "echo '=== Màn ngoài ==='; wm density -d $d; wm size -d $d"
        )
    }

    fun openLauncherOnCover(profile: DeviceProfile): Shell.Result {
        val p = resolveProfile(profile)
        ensureDual(p)
        val d = p.coverDisplay
        return Shell.exec(
            """
            settings put secure show_navigation_for_subscreen 1
            settings put system large_cover_screen_navigation 1
            settings put system large_cover_screen_apps 1
            cmd overlay enable com.android.internal.systemui.navbar.threebutton 2>/dev/null || true
            am start --display $d --activity-clear-task -n com.sec.android.app.launcher/com.sec.android.app.launcher.activities.LauncherActivity 2>/dev/null || am start --display $d -a android.intent.action.MAIN -c android.intent.category.HOME
            """.trimIndent().lines().joinToString("; ")
        )
    }

    fun openSettingsOnCover(profile: DeviceProfile): Shell.Result {
        val p = resolveProfile(profile)
        ensureDual(p)
        val d = p.coverDisplay
        return Shell.exec(
            """
            settings put secure show_navigation_for_subscreen 1
            settings put system large_cover_screen_navigation 1
            am start --display $d -n com.android.settings/.Settings
            """.trimIndent().lines().joinToString("; ")
        )
    }
}
