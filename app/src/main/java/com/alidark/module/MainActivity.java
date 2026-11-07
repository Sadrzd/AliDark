package com.alidark.module;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String[][] TARGETS = new String[][] {
            {"美团", "com.sankuai.meituan"},
            {"支付宝", "com.eg.android.AlipayGphone"},
            {"闲鱼", "com.taobao.idlefish"},
            {"淘宝", "com.taobao.taobao"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("AliDark 0.2.0");
        title.setTextSize(24);
        root.addView(title);

        TextView status = new TextView(this);
        if (ModuleStatus.active) {
            status.setText("模块状态：已激活");
            status.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            status.setText("模块状态：未检测到激活\n请在 LSPosed 中启用本模块后重启本应用查看。");
            status.setTextColor(Color.parseColor("#C62828"));
        }
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
                + "1. 在 LSPosed 中启用 AliDark，作用域仅勾选上述四个目标应用；\n"
                + "2. 强行停止并重新打开各目标应用（或重启手机一次）；\n"
                + "3. 切换系统深色/浅色模式验证效果。\n\n"
                + "注意：Force Dark 只能处理 Android 可自动转换的 View 和部分 WebView。"
                + "应用自绘页面、Flutter/自研渲染页面及部分图片/弹窗可能仍是浅色，需逐个版本适配。");
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
