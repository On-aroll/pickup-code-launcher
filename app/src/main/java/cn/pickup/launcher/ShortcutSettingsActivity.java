package cn.pickup.launcher;

import android.app.Activity;
import android.content.ClipData;
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
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShortcutSettingsActivity extends Activity {
    static final String KEY_OPEN_APP = "open_app";

    private static final String PREFS = "shortcut_selection";
    private static final String KEY_SELECTED = "selected_keys";
    private static final String KEY_ORDER = "order_keys";

    private static final int PAGE_BACKGROUND = Color.rgb(247, 248, 244);
    private static final int TEXT_PRIMARY = Color.rgb(27, 29, 27);
    private static final int TEXT_SECONDARY = Color.rgb(91, 96, 91);
    private static final int ACCENT = Color.rgb(20, 120, 72);

    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final ArrayList<String> order = new ArrayList<>();
    private LinearLayout listContainer;
    private String draggedKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(PAGE_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        selected.addAll(loadSelection(this));
        order.addAll(loadOrder(this));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(PAGE_BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("自定义快捷入口", 28, TEXT_PRIMARY, Typeface.BOLD);
        root.addView(title);

        TextView description = text(
                "勾选要显示的入口，长按拖动调整顺序。\n顺序同时作用于 App 首页与长按菜单。\n长按菜单最多显示前 4 项。",
                14,
                TEXT_SECONDARY,
                Typeface.NORMAL
        );
        description.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = dp(8);
        descriptionParams.bottomMargin = dp(18);
        description.setLayoutParams(descriptionParams);
        root.addView(description);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setOnDragListener(this::onContainerDrag);
        root.addView(listContainer);

        renderList();

        TextView saveButton = text("保存", 15, Color.WHITE, Typeface.BOLD);
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(rounded(ACCENT, 8));
        saveButton.setClickable(true);
        saveButton.setFocusable(true);
        saveButton.setContentDescription("保存入口设置");
        saveButton.setOnClickListener(view -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        saveParams.topMargin = dp(16);
        saveButton.setLayoutParams(saveParams);
        root.addView(saveButton);

        setContentView(scrollView);
    }

    private boolean startDrag(String key, View view) {
        draggedKey = key;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            view.startDragAndDrop(
                    ClipData.newPlainText("entry", key),
                    new View.DragShadowBuilder(view),
                    null,
                    0
            );
        } else {
            view.startDrag(
                    ClipData.newPlainText("entry", key),
                    new View.DragShadowBuilder(view),
                    null,
                    0
            );
        }
        return true;
    }

    private boolean onContainerDrag(View view, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DROP:
                if (draggedKey != null) {
                    int from = order.indexOf(draggedKey);
                    int to = indexAt(event.getY());
                    if (from >= 0 && to >= 0 && from != to) {
                        order.remove(from);
                        order.add(to, draggedKey);
                        renderList();
                    }
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                draggedKey = null;
                return true;
            default:
                return true;
        }
    }

    private int indexAt(float y) {
        int count = listContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = listContainer.getChildAt(i);
            if (y < child.getTop() + child.getHeight() / 2f) {
                return i;
            }
        }
        return count - 1;
    }

    private void renderList() {
        listContainer.removeAllViews();
        for (int i = 0; i < order.size(); i++) {
            listContainer.addView(buildRow(order.get(i), i));
        }
    }

    private View buildRow(final String key, final int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(3), dp(6), dp(3));

        CheckBox box = new CheckBox(this);
        box.setText(labelOf(this, key));
        box.setChecked(selected.contains(key));
        box.setTextSize(16);
        box.setTextColor(TEXT_PRIMARY);
        box.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selected.add(key);
            } else {
                selected.remove(key);
            }
        });
        row.addView(box, new LinearLayout.LayoutParams(0, dp(50), 1f));

        row.setLongClickable(true);
        row.setContentDescription("长按拖动" + labelOf(this, key));
        row.setOnLongClickListener(view -> startDrag(key, view));

        if (index > 0) {
            TextView up = arrowButton("↑", "上移" + labelOf(this, key));
            up.setOnClickListener(view -> {
                order.remove(key);
                order.add(index - 1, key);
                renderList();
            });
            row.addView(up, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }

        if (index < order.size() - 1) {
            TextView down = arrowButton("↓", "下移" + labelOf(this, key));
            down.setOnClickListener(view -> {
                order.remove(key);
                order.add(index + 1, key);
                renderList();
            });
            row.addView(down, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }

        return row;
    }

    private TextView arrowButton(String arrow, String description) {
        TextView button = text(arrow, 18, TEXT_PRIMARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.WHITE, 8));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(description);
        return button;
    }

    private void save() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "至少保留一个入口", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_SELECTED, new HashSet<>(selected))
                .putString(KEY_ORDER, join(order))
                .apply();
        applyDynamic(this);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    static List<String> loadOrder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_ORDER, null);
        if (saved != null && !saved.isEmpty()) {
            LinkedHashSet<String> keys = new LinkedHashSet<>(Arrays.asList(saved.split(",")));
            for (String key : allKeys(context)) {
                keys.add(key);
            }
            return new ArrayList<>(keys);
        }
        List<String> defaults = new ArrayList<>();
        for (Destination destination : Destination.values()) {
            defaults.add(destination.key);
        }
        defaults.add(KEY_OPEN_APP);
        return defaults;
    }

    static Set<String> loadSelection(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_SELECTED, null);
        if (saved == null) {
            return new HashSet<>(loadOrder(context));
        }
        return new HashSet<>(saved);
    }

    static void applyDynamic(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) {
            return;
        }

        List<String> order = loadOrder(context);
        Set<String> selected = loadSelection(context);
        List<ShortcutInfo> shortcuts = new ArrayList<>();
        int maxCount = 4;
        for (String key : order) {
            if (!selected.contains(key)) {
                continue;
            }
            if (shortcuts.size() >= maxCount) {
                break;
            }
            Intent intent;
            if (KEY_OPEN_APP.equals(key)) {
                intent = new Intent(context, MainActivity.class);
            } else {
                Destination destination = Destination.fromKey(key);
                if (destination == null) {
                    continue;
                }
                intent = new Intent(context, MainActivity.class)
                        .setAction(MainActivity.ACTION_OPEN)
                        .putExtra(MainActivity.EXTRA_DESTINATION, destination.key);
            }
            shortcuts.add(new ShortcutInfo.Builder(context, "dyn_" + key)
                    .setShortLabel(labelOf(context, key))
                    .setLongLabel("打开" + labelOf(context, key))
                    .setIcon(Icon.createWithResource(context, iconFor(key)))
                    .setIntent(intent)
                    .build());
        }
        manager.setDynamicShortcuts(shortcuts);
    }

    private static List<String> allKeys(Context context) {
        List<String> keys = new ArrayList<>();
        for (Destination destination : Destination.values()) {
            keys.add(destination.key);
        }
        keys.add(KEY_OPEN_APP);
        return keys;
    }

    private static String labelOf(Context context, String key) {
        if (KEY_OPEN_APP.equals(key)) {
            return context.getString(R.string.shortcut_open_app);
        }
        Destination destination = Destination.fromKey(key);
        return destination != null ? destination.title : key;
    }

    private static int iconFor(String key) {
        if (KEY_OPEN_APP.equals(key)) {
            return R.drawable.ic_launcher;
        }
        Destination destination = Destination.fromKey(key);
        if (destination == null) {
            return R.drawable.ic_launcher;
        }
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

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
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
