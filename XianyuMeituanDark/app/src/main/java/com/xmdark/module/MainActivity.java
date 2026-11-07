package com.xmdark.module;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String[][] TARGETS = {
            {"闲鱼", "com.taobao.idlefish"},
            {"美团", "com.sankuai.meituan"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("闲鱼美团深色 1.0.0");
        title.setTextSize(24);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText("是否生效请以 LSPosed 管理器为准：\n只要在本模块的作用域里勾选了"
                + "「闲鱼」和「美团」即为启用。本应用无法自动检测模块注入状态。");
        status.setTextColor(Color.parseColor("#616161"));
        status.setTextSize(16);
        status.setPadding(0, pad, 0, 0);
        root.addView(status);

        TextView targets = new TextView(this);
        targets.setText(buildTargetSummary());
        targets.setTextSize(16);
        targets.setPadding(0, pad, 0, 0);
        root.addView(targets);

        TextView body = new TextView(this);
        body.setText("使用步骤：\n"
                + "1. 在 LSPosed 中启用本模块，作用域仅勾选「闲鱼」和「美团」；\n"
                + "2. 强行停止并重新打开这两个应用（或重启手机一次）；\n"
                + "3. 切换系统深色/浅色模式验证，两个应用应跟随系统变化。\n\n"
                + "说明：本模块使用 Android 原生 Force Dark 处理普通页面，"
                + "使用算法深色处理 WebView/H5 页面。自绘页面、Flutter/自研渲染"
                + "页面及少数图片/二维码可能仍为浅色，需按版本单独适配。");
        body.setTextSize(14);
        body.setPadding(0, pad, 0, 0);
        root.addView(body);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private String buildTargetSummary() {
        StringBuilder sb = new StringBuilder("目标应用：\n");
        PackageManager pm = getPackageManager();
        for (String[] target : TARGETS) {
            boolean installed;
            try {
                pm.getPackageInfo(target[1], 0);
                installed = true;
            } catch (PackageManager.NameNotFoundException e) {
                installed = false;
            }
            sb.append(installed ? "● " : "○ ")
                    .append(target[0])
                    .append(installed ? "（已安装）" : "（未安装）")
                    .append('\n');
        }
        return sb.toString().trim();
    }
}