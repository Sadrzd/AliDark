package com.alidark.module;

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
 * AliDark: forces Android's native Force Dark and WebView darkening in the
 * target apps, gated on the system night mode so the apps follow the system
 * dark/light switch.
 *
 * Important: this does not rewrite proprietary/custom drawing code inside
 * target apps (Flutter/Compose/self-rendered pages may stay light).
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
        hookPopupWindow(lpparam);
        hookWebView(lpparam);
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

    // ---------------------------------------------------------------------
    // Native views
    // ---------------------------------------------------------------------

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
            // onResume covers activities coming back to the foreground.
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, applyHook);
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", applyHook);

            // Target apps usually declare configChanges and are NOT recreated
            // when the system night mode toggles; re-apply everything manually.
            XposedHelpers.findAndHookMethod(Activity.class, "onConfigurationChanged",
                    Configuration.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity activity = (Activity) param.thisObject;
                                Window window = activity.getWindow();
                                if (window != null) {
                                    View decor = window.getDecorView();
                                    applyForceDark(decor);
                                    applyToWebViewsInTree(decor);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "config change apply failed: " + t);
                            }
                        }
                    });
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

    private static void hookPopupWindow(XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook applyHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    PopupWindow popup = (PopupWindow) param.thisObject;
                    applyForceDark(popup.getContentView());
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "PopupWindow apply failed: " + t);
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(PopupWindow.class, "showAtLocation", applyHook);
            XposedBridge.hookAllMethods(PopupWindow.class, "showAsDropDown", applyHook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "PopupWindow hook failed: " + t);
        }
    }

    private static void applyForceDark(View root) {
        if (root == null || Build.VERSION.SDK_INT < 29) return;
        try {
            // FORCE_DARK_ALLOWED lets Android's automatic Force Dark algorithm
            // transform eligible light-themed Views while in night mode.
            // The flag inherits down the tree unless a child opts out.
            root.setForceDarkAllowed(true);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "setForceDarkAllowed failed: " + t);
        }
    }

    // ---------------------------------------------------------------------
    // WebView
    // ---------------------------------------------------------------------

    private static void hookWebView(XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook applyHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    applyToWebViewOnce((WebView) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "WebView apply failed: " + t);
                }
            }
        };
        try {
            // Attach covers declaratively created WebViews.
            XposedHelpers.findAndHookMethod(WebView.class, "onAttachedToWindow", applyHook);
            // Load hooks guarantee the settings land before any page content.
            XposedBridge.hookAllMethods(WebView.class, "loadUrl", applyHook);
            XposedBridge.hookAllMethods(WebView.class, "loadData", applyHook);
            XposedBridge.hookAllMethods(WebView.class, "loadDataWithBaseURL", applyHook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "WebView hook failed: " + t);
        }
    }

    private static void applyToWebViewsInTree(View view) {
        if (view == null) return;
        try {
            if (view instanceof WebView) {
                // Configuration changed (e.g. night mode toggled): re-evaluate
                // even WebViews that were already configured.
                reapplyToWebView((WebView) view);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyToWebViewsInTree(group.getChildAt(i));
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

    /**
     * WebViews already configured in this process. Settings are applied at
     * most once per instance: calling WebView provider APIs repeatedly (in
     * particular after content has loaded) can break page rendering.
     */
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

            // Only touch the WebView while the system is in night mode. In
            // light mode the default state already renders normally, and
            // every provider call is one more chance to break the page.
            if (!isNightMode(webView.getContext())) return true;

            if (Build.VERSION.SDK_INT >= 33) {
                // Algorithmic darkening is the official mechanism since
                // Android 13 (setForceDark is deprecated and may stop working
                // on Android 16 / OneUI 8.5). Fall back to force dark if the
                // WebView provider rejects it.
                try {
                    settings.setAlgorithmicDarkeningAllowed(true);
                    return true;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "algorithmic darkening failed: " + t);
                }
            }
            // FORCE_DARK_AUTO depends on the host app theme opting in, which
            // the target apps disabled, so darken explicitly.
            settings.setForceDark(WebSettings.FORCE_DARK_ON);
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "WebView settings failed: " + t);
            return false;
        }
    }
}
