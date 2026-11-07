package com.alidark.module;

import android.app.Activity;
import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * AliDark: asks Android's native Force Dark implementation and WebView
 * to follow the host application's current system night mode.
 *
 * Important: this does not rewrite proprietary/custom drawing code inside target apps.
 */
public final class AliDarkHook implements IXposedHookLoadPackage {
    private static final String TAG = "[AliDark] ";
    private static final String SELF_PACKAGE = "com.alidark.module";

    private static final String[] TARGET_PACKAGES = new String[] {
            "com.sankuai.meituan",
            "com.eg.android.AlipayGphone",
            "com.taobao.idlefish",
            "com.taobao.taobao"
    };

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (SELF_PACKAGE.equals(lpparam.packageName)) {
            hookSelfStatus(lpparam);
            return;
        }
        if (!isTarget(lpparam.packageName)) return;

        XposedBridge.log(TAG + "loaded for " + lpparam.packageName);

        hookActivity(lpparam);
        hookDialog(lpparam);
        hookWebViewAttach(lpparam);
    }

    private static boolean isTarget(String packageName) {
        for (String target : TARGET_PACKAGES) {
            if (target.equals(packageName)) return true;
        }
        return false;
    }

    /**
     * Marks the module as active when its own MainActivity is created,
     * so the app can display an activation indicator.
     */
    private static void hookSelfStatus(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(SELF_PACKAGE + ".MainActivity",
                    lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ModuleStatus.active = true;
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "self status hook failed: " + t);
        }
    }

    private static void hookActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook applyHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Activity activity = (Activity) param.thisObject;
                    Window window = activity.getWindow();
                    if (window != null) applyForceDark(window.getDecorView());
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Activity apply failed: " + t);
                }
            }
        };
        try {
            // onCreate applies early so the first frame is already dark;
            // onResume covers activities recreated by theme changes.
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, applyHook);
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", applyHook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Activity hook failed: " + t);
        }
    }

    private static void hookDialog(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Dialog dialog = (Dialog) param.thisObject;
                        Window window = dialog.getWindow();
                        if (window != null) applyForceDark(window.getDecorView());
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "Dialog apply failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Dialog hook failed: " + t);
        }
    }

    private static void applyForceDark(View decor) {
        if (decor == null || Build.VERSION.SDK_INT < 29) return;
        try {
            // FORCE_DARK_ALLOWED lets Android's automatic Force Dark algorithm
            // transform eligible light-themed Views when the system is in dark mode.
            decor.setForceDarkAllowed(true);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "setForceDarkAllowed failed: " + t);
        }
    }

    private static void hookWebViewAttach(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(WebView.class, "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                applyToWebView((WebView) param.thisObject);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "WebView apply failed: " + t);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "WebView hook failed: " + t);
        }
    }

    private static void applyToWebView(WebView webView) {
        if (webView == null || Build.VERSION.SDK_INT < 29) return;
        try {
            WebSettings settings = webView.getSettings();
            if (settings != null) {
                // AUTO follows the host app's current system/theme night mode;
                // it does not force dark content while the phone is in light mode.
                settings.setForceDark(WebSettings.FORCE_DARK_AUTO);
                settings.setForceDarkStrategy(
                        WebSettings.FORCE_DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "WebView settings failed: " + t);
        }
    }
}
