package app.coverctl

/**
 * Khôi phục DeX trên màn HDMI — desktop mode + density + SecondaryLauncher + mở cài đặt DeX.
 */
object DexCommands {

    private const val SECONDARY =
        "com.sec.android.app.launcher/com.honeyspace.dexservice.SecondaryLauncher"

    /** Các activity cài đặt DeX / Desktop thường gặp trên One UI. */
    private val SETTINGS_CANDIDATES = listOf(
        "com.samsung.desktopsystemui/.settings.DesktopSettingsActivity",
        "com.samsung.desktopsystemui/com.samsung.desktopsystemui.settings.DesktopSettingsActivity",
        "com.sec.android.app.desktoplauncher/.settings.DesktopSettingsActivity",
        "com.samsung.android.app.telephonyui/.hiddenmenu.DesktopModeActivity",
        "com.android.settings/.Settings\$ConnectedDeviceDashboardActivity",
        "com.android.settings/.Settings\$AdvancedConnectedDeviceActivity",
        "com.android.settings/.Settings"
    )

    fun enableDex(): Shell.Result =
        Shell.exec(
            """
            echo '=== Bật DeX (desktop mode) ==='
            settings put global force_desktop_mode_on_external_displays 1
            settings put global enable_freeform_support 1
            settings put global enable_non_resizable_multi_window 1
            settings put global force_resizable_activities 1
            settings put global dex_on_external_display 1
            settings put global taskbar_recent_apps_enabled 1
            settings put global taskbar_max_recent_count 12
            settings put global freeform_caption_type 1
            settings put global mirror_built_in_display 0
            settings put system mirror_built_in_display 0 2>/dev/null || true
            settings put global desktop_mode_enabled 1 2>/dev/null || true
            settings put system dexonpc_connection_state 1 2>/dev/null || true
            echo 'Flags đã ghi. Cắm HDMI/hub rồi bấm «Mở DeX trên màn hình» hoặc «Cài đặt DeX».'
            settings get global force_desktop_mode_on_external_displays
            settings get global dex_on_external_display
            settings get global mirror_built_in_display
            """.trimIndent().lines().joinToString("; ")
        )

    fun disableDex(): Shell.Result =
        Shell.exec(
            """
            echo '=== Tắt DeX / về stock ==='
            settings put global force_desktop_mode_on_external_displays 0
            settings put global enable_freeform_support 0
            settings put global enable_non_resizable_multi_window 0
            settings put global force_resizable_activities 0
            settings put global dex_on_external_display 0
            settings put global taskbar_recent_apps_enabled 0
            settings delete global mirror_built_in_display 2>/dev/null || true
            settings put global desktop_mode_enabled 0 2>/dev/null || true
            am broadcast -a android.app.action.EXIT_KNOX_DESKTOP_MODE 2>/dev/null || true
            echo 'Đã khôi phục cài đặt stock'
            """.trimIndent().lines().joinToString("; ")
        )

    /**
     * Mở trang cài đặt DeX / Connected devices — thử nhiều component One UI.
     */
    fun openDexSettings(): Shell.Result {
        val tries = SETTINGS_CANDIDATES.joinToString("; ") { comp ->
            "echo -- try $comp; " +
                "am start -n $comp 2>/dev/null && echo OK_SETTINGS=$comp && exit 0; " +
                "true"
        }
        return Shell.exec(
            """
            echo '=== Mở Cài đặt DeX ==='
            am start -a com.samsung.android.desktopmode.action.OPEN_DESKTOP_MODE_SETTINGS 2>/dev/null && echo OK_ACTION=OPEN_DESKTOP_MODE_SETTINGS && exit 0
            am start -a com.samsung.android.desktopmode.action.ENTER_DESKTOP_MODE 2>/dev/null && echo OK_ACTION=ENTER_DESKTOP_MODE
            am start -a android.settings.CONNECTED_DEVICES_SETTINGS 2>/dev/null && echo OK_ACTION=CONNECTED_DEVICES
            $tries
            # Tìm activity có chữ Desktop/DeX trong launcher + settings
            echo '--- tìm activity DeX/Desktop trên máy ---'
            cmd package query-activities --brief -a android.intent.action.MAIN 2>/dev/null | grep -iE 'dex|desktop' | head -n 20
            pm dump com.samsung.desktopsystemui 2>/dev/null | grep -i 'Activity' | head -n 20
            pm dump com.sec.android.app.launcher 2>/dev/null | grep -iE 'SecondaryLauncher|Desktop|Dex' | head -n 20
            echo 'Nếu không mở được Settings DeX: One UI đã gỡ UI — dùng Bật DeX + HDMI + Mở DeX trên màn hình.'
            """.trimIndent().lines().joinToString("; ")
        )
    }

    /**
     * Tìm display ngoài (không phải 0/1), ép density DeX, mở SecondaryLauncher.
     */
    fun startDexOnExternal(): Shell.Result =
        Shell.exec(
            """
            echo '=== Mở DeX trên màn ngoài ==='
            settings put global force_desktop_mode_on_external_displays 1
            settings put global enable_freeform_support 1
            settings put global enable_non_resizable_multi_window 1
            settings put global dex_on_external_display 1
            settings put global mirror_built_in_display 0
            echo '--- displays ---'
            dumpsys display | tr '}' '\n' | grep -E 'displayId [0-9]+' | head -n 40
            # Ưu tiên TYPEC/EXTERNAL (log SCG23: display 14 = TYPEC 1920x1200)
            EXT=""
            for cand in 14 6 2 3 4 5 7 8 9 10 11 12 13 15 16; do
              block=$(dumpsys display 2>/dev/null | tr '}' '\n' | grep -E "displayId ${'$'}cand|TYPEC|EXTERNAL|HDMI" | head -n 5)
              info=$(dumpsys display 2>/dev/null | tr '}' '\n' | grep "displayId ${'$'}cand" | head -n 1)
              if [ -n "${'$'}info" ]; then
                echo "cand=${'$'}cand :: ${'$'}info" | head -c 200; echo
                echo "${'$'}info" | grep -qiE 'EXTERNAL|TYPEC|HDMI|DisplayPort' && EXT="${'$'}cand" && break
              fi
            done
            if [ -z "${'$'}EXT" ]; then
              # Lấy id TYPEC từ DisplayDeviceInfo
              EXT=$(dumpsys display 2>/dev/null | tr '}' '\n' | grep -B2 'TYPEC' | sed -n 's/.*displayId \([0-9][0-9]*\).*/\1/p' | head -n 1)
            fi
            if [ -z "${'$'}EXT" ]; then
              EXT=$(dumpsys display 2>/dev/null | tr '}' '\n' | sed -n 's/.*displayId \([0-9][0-9]*\).*/\1/p' | grep -vE '^[01]$' | sort -nu | head -n 1)
            fi
            if [ -z "${'$'}EXT" ]; then
              echo 'KHÔNG thấy màn HDMI/TYPEC. Cắm hub có hình rồi thử lại.'
              exit 2
            fi
            echo "Dùng display ${'$'}EXT (DeX)"
            # Bật lại màn nếu đang OFF
            dumpsys display 2>/dev/null | tr '}' '\n' | grep -A2 "displayId ${'$'}EXT" | head -n 8
            wm density 160 -d ${'$'}EXT
            wm size reset -d ${'$'}EXT 2>/dev/null || true
            # Không ép 1920x1080 nếu panel là 1920x1200
            am broadcast -a android.app.action.ENTER_KNOX_DESKTOP_MODE --ei android.intent.extra.DISPLAY_ID ${'$'}EXT 2>/dev/null || true
            am broadcast -a com.samsung.android.desktopmode.action.ENTER_DESKTOP_MODE --ei android.intent.extra.DISPLAY_ID ${'$'}EXT 2>/dev/null || true
            am force-stop com.sec.android.app.launcher 2>/dev/null || true
            sleep 0.5
            am start --display ${'$'}EXT --activity-clear-task -n $SECONDARY
            am start --display ${'$'}EXT -n $SECONDARY
            am start --display ${'$'}EXT -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null || true
            am start --display ${'$'}EXT -a android.intent.action.MAIN -c android.intent.category.DESK_DOCK 2>/dev/null || true
            echo "Đã gọi SecondaryLauncher trên display ${'$'}EXT"
            echo 'Nếu màn TYPEC state OFF: rút/cắm lại cáp rồi bấm lại.'
            """.trimIndent().lines().joinToString("; ")
        )

    fun dexStatus(): Shell.Result =
        Shell.exec(
            """
            echo '=== Trạng thái DeX ==='
            echo -n 'force_desktop='; settings get global force_desktop_mode_on_external_displays
            echo -n 'freeform='; settings get global enable_freeform_support
            echo -n 'dex_on_external='; settings get global dex_on_external_display
            echo -n 'non_resizable_mw='; settings get global enable_non_resizable_multi_window
            echo -n 'mirror_built_in='; settings get global mirror_built_in_display
            echo -n 'taskbar_recent='; settings get global taskbar_recent_apps_enabled
            echo -n 'desktop_mode_enabled='; settings get global desktop_mode_enabled
            echo '--- SecondaryLauncher ---'
            cmd package resolve-activity --brief -n $SECONDARY 2>&1
            pm path com.samsung.desktopsystemui 2>&1 | head -n 3
            pm path com.sec.android.app.desktoplauncher 2>&1 | head -n 3
            echo '--- activities DeX/Desktop ---'
            cmd package query-activities --brief -a android.intent.action.MAIN 2>/dev/null | grep -iE 'dex|desktop' | head -n 25
            echo '--- displays ---'
            dumpsys display | tr '}' '\n' | grep -E 'DisplayInfo\{|displayId |type |state |uniqueId' | head -n 60
            """.trimIndent().lines().joinToString("; ")
        )
}
