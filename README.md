# CoverCtl

Control the Galaxy Z Flip **cover screen**, **Samsung DeX**, and **Android Auto** helpers from one app — without needing the inner display.

**Languages:** Tiếng Việt · English · 中文 · 日本語  
*(App settings → choose language)*

## Features

- **Privilege channels (pick one):** Shizuku **or** wireless ADB — independent, not mixed
- **Screen modes (flip-ctl style):** Main / Cover only / Dual, keep Dual after sleep, Quick Settings tile
- **Cover navigation overlay:** Back / Home / Recents on the cover (SystemUI has no nav bar there), plus floating panel button
- **One Hand / edge gestures (cover):** OHO+-style side handles on the cover (Samsung OHO+ does not support Flip cover); shortcuts to Good Lock / MultiStar / install OHO+ for the main screen
- **Galaxy Wi‑Fi 5G widget:** multi-info panel (network / signal / battery / data / hotspot) synced with the phone, add to home or cover via MultiStar
- **Cover UI:** full cover / Fold open mode, DPI scale, open Launcher/Settings on cover
- **Identity presets:** Flip 5/6/7, Fold 5/6 (spoof needs Magisk; display works without root)
- **DeX:** enable flags, open on external display, DeX settings / status
- **Android Auto:** checklist, UnknownAuto / AnyAuto links, Play-mark install helper
- **Uninstall** apps via shell on the selected channel
- **Logs:** export / share session logs
- **Settings:** language, version info, check updates from GitHub Releases

## Requirements

- Android 9+ (tested on Galaxy Z Flip / SCG23)
- [Shizuku](https://shizuku.rikka.app/) **or** Wireless debugging (ADB)

## Install

1. Download the latest APK from [Releases](https://github.com/nguyenthemanh/CoverCtl/releases)
2. Or build:

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Update check

In the app: **App settings → Check for updates (GitHub)** — reads the latest release from this repository.

## Privacy / notes

- Shell commands run only on the privilege channel you select.
- Spoofing `ro.*` properties requires Magisk (`resetprop`). Without root, use **Full cover / Fold open** for display.

## License

Personal / experimental project. Use at your own risk on your device.

---

### Tiếng Việt

Ứng dụng điều khiển **màn ngoài** Flip, **DeX** và hỗ trợ **Android Auto**. Chọn quyền qua Shizuku hoặc ADB không dây. Trong **Cài đặt ứng dụng** có chọn ngôn ngữ, xem phiên bản và kiểm tra cập nhật từ GitHub.

### 中文

用于 Galaxy Z Flip **外屏**、**DeX** 与 **Android Auto** 辅助。权限通道为 Shizuku 或无线 ADB（二选一）。在**应用设置**中可切换语言、查看版本并从 GitHub 检查更新。

### 日本語

Galaxy Z Flip の**カバー画面**、**DeX**、**Android Auto** 補助アプリ。権限は Shizuku またはワイヤレス ADB のどちらか。**アプリ設定**で言語切替・バージョン表示・GitHub 更新確認ができます。
