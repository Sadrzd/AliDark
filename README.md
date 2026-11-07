# AliDark (prototype 0.1.0)

A legacy-Xposed/LSPosed module prototype for requesting Android native Force Dark and WebView automatic darkening in four target apps.

## Target packages
- Meituan: `com.sankuai.meituan`
- Alipay: `com.eg.android.AlipayGphone`
- Xianyu: `com.taobao.idlefish`
- Taobao: `com.taobao.taobao`

## Behavior
- Hooks `Activity.onResume()` and enables `View.setForceDarkAllowed(true)` on the Activity decor view (Android 10+).
- Hooks `WebView.onAttachedToWindow()` and sets WebView Force Dark to `FORCE_DARK_AUTO`.
- AUTO is intended to follow the host app/system theme; it is not an always-dark override.

## Build
Requirements: JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.7.3, Gradle 8.9 or compatible.

From this directory run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install / enable
1. Build and install the APK.
2. Open LSPosed and enable AliDark.
3. In module scope, select only the four target apps listed above.
4. Force-stop/reopen each target app (or reboot once).
5. Switch Android system dark/light mode and test.

## Limitations
This is a best-effort compatibility prototype, not a guaranteed complete dark theme. Native Force Dark is only effective for eligible Android View hierarchies. Apps may use custom-rendered pages, Compose/Flutter, proprietary UI frameworks, or WebView content that opts out of darkening. Such screens need app-version-specific hooks and visual testing. Product photos, maps, QR codes, and custom dialogs may not be transformed consistently.

No app-private APIs are hooked and no target APK is modified. The module catches hook errors to reduce crash risk, but test on a backup/test profile first.
