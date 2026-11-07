# AliDark (prototype 0.2.0)

A legacy-Xposed/LSPosed module that makes four target apps follow the Android system dark/light mode, using native Force Dark for Views and forced darkening for WebView.

## Target packages
- Meituan: `com.sankuai.meituan`
- Alipay: `com.eg.android.AlipayGphone`
- Xianyu: `com.taobao.idlefish`
- Taobao: `com.taobao.taobao`

## Behavior
- Hooks `Activity.onCreate()` / `onResume()` and enables `View.setForceDarkAllowed(true)` on the decor view (Android 10+). The flag inherits down the view tree.
- Hooks `Activity.onConfigurationChanged()` so toggling system dark mode applies immediately even in apps that suppress recreation via `configChanges`.
- Hooks `Dialog.show()` and `PopupWindow.show*()` so dialogs and popups are covered too.
- WebView: hooks `onAttachedToWindow()` plus all `loadUrl` / `loadData*` variants so settings always land before page content loads.
  - System night mode ON → `FORCE_DARK_ON` (Android 13+: `setAlgorithmicDarkeningAllowed(true)`).
  - System night mode OFF → `FORCE_DARK_OFF`, pages render normally.
  - This is a follow-the-system switch, not an always-dark override.
- The launcher activity shows module activation status and which target apps are installed.

## Build
Requirements: JDK 17, Android SDK Platform 35. The Gradle 8.9 wrapper is checked in.

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install / enable
1. Build and install the APK.
2. Open LSPosed and enable AliDark.
3. In module scope, select only the four target apps listed above (they are pre-suggested via `xposedscope`).
4. Force-stop/reopen each target app (or reboot once).
5. Switch Android system dark/light mode and test. Open the AliDark app to confirm it reports 已激活.

## Limitations
This is a best-effort compatibility prototype, not a guaranteed complete dark theme. Native Force Dark is only effective for eligible Android View hierarchies, and views that explicitly opt out stay light. Apps may use custom-rendered pages, Compose/Flutter, proprietary UI frameworks, or WebView content that opts out of darkening. Such screens need app-version-specific hooks and visual testing. Product photos, maps, QR codes, and custom dialogs may not be transformed consistently.

No app-private APIs are hooked and no target APK is modified. The module catches hook errors to reduce crash risk, but test on a backup/test profile first.
