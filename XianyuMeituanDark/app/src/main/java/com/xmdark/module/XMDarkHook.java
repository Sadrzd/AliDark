package com.xmdark.module;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.PopupWindow;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * XMDark: makes Xianyu (闲鱼) and Meituan (美团) follow the system dark/light
 * mode. Native View trees are handled with Android's Force Dark algorithm and
 * WebView content with algorithmic darkening, both gated on the system night
 * mode so the apps stay in sync with a Samsung OneUI dark-mode toggle.
 */
public final class XMDarkHook implements IXposedHookLoadPackage {

    private static final String TAG = "[XMDark] ";
    private static final String SELF_PACKAGE = "com.xmdark.module";

    private static final String[] TARGET_PACKAGES = {
            "com.taobao.idlefish",   // 闲鱼
            "com.sankuai.meituan"    // 美团
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (SELF_PACKAGE.equals(lpparam.packageName)) {
            hookSelfStatus(lpparam);
            return;
        }
        if (!isTarget(lpparam.packageName)) return;

        XposedBridge.log(TAG + "attached to " + lpparam.packageName);

        hookActivity(lpparam);
        hookDialog();
        hookPopupWindow();
        hookWebView();
    }

    private static boolean isTarget(String packageName) {
        for (String target : TARGET_PACKAGES) {
            if (target.equals(packageName)) return true;
        }
        return false;
    }

    /** Flips the module's own wire flag so the launcher can show 已激活. */
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

    // ------------------------------------------------------------------ Views

    private static void hookActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook applyForce = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                Window window = activity.getWindow();
                if (window != null) applyForceDark(window.getDecorView());
            }
        };
        XC_MethodHook onConfigChanged = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                Window window = activity.getWindow();
                if (window == null) return;
                applyForceDark(window.getDecorView());
                reapplyWebViewsInTree(window.getDecorView());
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, applyForce);
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", applyForce);
            XposedHelpers.findAndHookMethod(Activity.class, "onPostResume", applyForce);
            XposedHelpers.findAndHookMethod(Activity.class, "onConfigurationChanged",
                    Configuration.class, onConfigChanged);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Activity hook failed: " + t);
        }
    }

    private static void hookDialog() {
        try {
            XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Dialog dialog = (Dialog) param.thisObject;
                    Window window = dialog.getWindow();
                    if (window != null) applyForceDark(window.getDecorView());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Dialog hook failed: " + t);
        }
    }

    private static void hookPopupWindow() {
        XC_MethodHook applyForce = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                PopupWindow popup = (PopupWindow) param.thisObject;
                View content = popup.getContentView();
                if (content != null) applyForceDark(content);
            }
        };
        try {
            XposedBridge.hookAllMethods(PopupWindow.class, "showAtLocation", applyForce);
            XposedBridge.hookAllMethods(PopupWindow.class, "showAsDropDown", applyForce);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "PopupWindow hook failed: " + t);
        }
    }

    /**
     * Marks an inflated tree eligible for Force Dark. The flag normally
     * inherits down on inflation, but already-inflated light trees need an
     * explicit recursive pass to take effect.
     */
    private static void applyForceDark(View root) {
        if (root == null || Build.VERSION.SDK_INT < 29) return;
        try {
            root.setForceDarkAllowed(true);
        } catch (Throwable ignored) {
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyForceDark(group.getChildAt(i));
            }
        }
    }

    // ----------------------------------------------------------------- WebView

    private static void hookWebView() {
        XC_MethodHook apply = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                applyToWebViewOnce((WebView) param.thisObject);
            }
        };
        try {
            XposedHelpers.findAndHookMethod(WebView.class, "onAttachedToWindow", apply);
            XposedBridge.hookAllMethods(WebView.class, "loadUrl", apply);
            XposedBridge.hookAllMethods(WebView.class, "loadData", apply);
            XposedBridge.hookAllMethods(WebView.class, "loadDataWithBaseURL", apply);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "WebView hook failed: " + t);
        }
    }

    private static void reapplyWebViewsInTree(View view) {
        if (view == null) return;
        try {
            if (view instanceof WebView) {
                reapplyToWebView((WebView) view);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    reapplyWebViewsInTree(group.getChildAt(i));
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "WebView tree apply failed: " + t);
        }
    }

    private static boolean isNightMode(Context context) {
        if (context == null) return false;
        int mask = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    /** WebViews already configured in this process (weakly referenced). */
    private static final Set<WebView> APPLIED_WEBVIEWS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<WebView, Boolean>()));

    private static void applyToWebViewOnce(WebView webView) {
        if (webView == null || Build.VERSION.SDK_INT < 29) return;
        if (APPLIED_WEBVIEWS.contains(webView)) return;
        if (applyToWebView(webView)) {
            APPLIED_WEBVIEWS.add(webView);
        }
    }

    private static void reapplyToWebView(WebView webView) {
        if (webView == null) return;
        APPLIED_WEBVIEWS.remove(webView);
        applyToWebViewOnce(webView);
    }

    private static boolean applyToWebView(WebView webView) {
        if (webView == null || Build.VERSION.SDK_INT < 29) return true;
        try {
            WebSettings settings = webView.getSettings();
            if (settings == null) return false;

            boolean night = isNightMode(webView.getContext());

            if (Build.VERSION.SDK_INT >= 33) {
                // Official path since Android 13; setForceDark is deprecated
                // and may become a no-op on Android 16 / OneUI 8.x.
                try {
                    settings.setAlgorithmicDarkeningAllowed(night);
                    return true;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "algorithmic darkening failed: " + t);
                }
            }
            settings.setForceDark(night ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "WebView settings failed: " + t);
            return false;
        }
    }
}