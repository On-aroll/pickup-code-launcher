package cn.pickup.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ShortcutSettingsActivity extends Activity {
    private static final String PREFS = "shortcut_selection";
    private static final String KEY_SELECTED = "selected_keys";

    private final Set<String> selected = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(247, 248, 244));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        selected.addAll(loadSelection(this));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 248, 244));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("自定义长按菜单", 28, Color.rgb(27, 29, 27), Typeface.BOLD);
        root.addView(title);

        TextView description = text(
                "选择长按桌面图标时显示的快捷入口。",
                14,
                Color.rgb(91, 96, 91),
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = dp(8);
        descriptionParams.bottomMargin = dp(18);
        description.setLayoutParams(descriptionParams);
        root.addView(description);

        for (Destination destination : Destination.values()) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(destination.title);
            checkBox.setTextSize(16);
            checkBox.setTextColor(Color.rgb(27, 29, 27));
            checkBox.setChecked(selected.contains(destination.key));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selected.add(destination.key);
                } else {
                    selected.remove(destination.key);
                }
            });
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
            );
            checkBox.setLayoutParams(checkParams);
            root.addView(checkBox);
        }

        TextView saveButton = text("保存", 15, Color.WHITE, Typeface.BOLD);
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(rounded(Color.rgb(20, 120, 72), 8));
        saveButton.setClickable(true);
        saveButton.setFocusable(true);
        saveButton.setContentDescription("保存长按菜单设置");
        saveButton.setOnClickListener(view -> {
            saveSelection(this, selected);
            applyDynamic(this);
            Toast.makeText(this, "已更新长按菜单", Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        saveParams.topMargin = dp(10);
        saveButton.setLayoutParams(saveParams);
        root.addView(saveButton);

        setContentView(scrollView);
    }

    static Set<String> loadSelection(Context context) {
        Set<String> saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_SELECTED, null);
        if (saved == null) {
            Set<String> all = new HashSet<>();
            for (Destination destination : Destination.values()) {
                all.add(destination.key);
            }
            return all;
        }
        return new HashSet<>(saved);
    }

    private static void saveSelection(Context context, Set<String> selection) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_SELECTED, new HashSet<>(selection))
                .apply();
    }

    static void applyDynamic(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) {
            return;
        }

        Set<String> keys = loadSelection(context);
        List<ShortcutInfo> shortcuts = new ArrayList<>();
        for (Destination destination : Destination.values()) {
            if (!keys.contains(destination.key)) {
                continue;
            }
            Intent intent = new Intent(context, MainActivity.class)
                    .setAction(MainActivity.ACTION_OPEN)
                    .putExtra(MainActivity.EXTRA_DESTINATION, destination.key);
            shortcuts.add(new ShortcutInfo.Builder(context, "dyn_" + destination.key)
                    .setShortLabel(destination.title)
                    .setLongLabel("打开" + destination.title)
                    .setIcon(Icon.createWithResource(context, iconFor(destination)))
                    .setIntent(intent)
                    .build());
        }

        if (!shortcuts.isEmpty()) {
            manager.setDynamicShortcuts(shortcuts);
        }
    }

    private static int iconFor(Destination destination) {
        switch (destination) {
            case CAINIAO:
            case CAINIAO_PACKAGES:
                return R.drawable.ic_shortcut_cainiao;
            case TAOBAO:
            case TAOBAO_PENDING:
                return R.drawable.ic_shortcut_taobao;
            case PINDUODUO:
            case PINDUODUO_PENDING:
                return R.drawable.ic_shortcut_pinduoduo;
            case JD:
                return R.drawable.ic_shortcut_jd;
            case XHS:
            default:
                return R.drawable.ic_shortcut_xhs;
        }
    }

    private TextView text(String value, float sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
