package cn.pickup.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pickup checklist: import -> review -> group by location -> mark collected. */
public final class ParcelActivity extends Activity {
    private static final int PICK_IMAGES = 28;
    private static final int INK = Color.rgb(27, 40, 35);
    private static final int MUTED = Color.rgb(86, 101, 93);
    private static final int GREEN = Color.rgb(22, 100, 73);
    private ParcelStore store;
    private LinearLayout list;
    private TextView totals, progress;
    private EditText search;
    private final Button[] tabs = new Button[3];
    private Button imagesButton, pasteButton, reviewButton, stopButton;
    private int selectedTab;
    private long seenRevision = -1;
    private boolean wasRunning;
    private AlertDialog dialog;
    private AlertDialog confirmation;
    private Parcel editingParcel;
    private EditText[] editorFields;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            updateProgress();
            if (seenRevision != ParcelImporter.revision || wasRunning != ParcelImporter.running) {
                seenRevision = ParcelImporter.revision;
                wasRunning = ParcelImporter.running;
                refresh();
            }
            handler.postDelayed(this, 700);
        }
    };

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        store = new ParcelStore(this);
        ShortcutSettingsActivity.applyDynamic(this);
        if (savedState != null) selectedTab = savedState.getInt("tab", 0);
        getWindow().setStatusBarColor(Color.rgb(244, 247, 241));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 241));
        scroll.setFitsSystemWindows(true);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root);
        root.addView(label("我的待取快递", 28, INK, true));
        root.addView(label("按地点收好取件码，到站一次取齐。", 14, MUTED, false));
        totals = label("", 19, GREEN, true);
        root.addView(totals, vertical(18));

        LinearLayout importActions = row();
        imagesButton = button("导入多张截图", this::pickImages);
        pasteButton = button("粘贴通知", this::pasteText);
        addWeighted(importActions, imagesButton);
        addWeighted(importActions, pasteButton);
        root.addView(importActions, vertical(12));
        LinearLayout actions = row();
        addWeighted(actions, button("手动补一件", () -> edit(new Parcel())));
        addWeighted(actions, button("平台入口", () -> startActivity(new Intent(this, MainActivity.class)
                .putExtra("show_platform_links", true))));
        root.addView(actions);
        progress = label("", 13, MUTED, false);
        root.addView(progress, vertical(8));
        stopButton = button("停止后续图片", () -> {
            ParcelImporter.cancelRequested = true;
            stopButton.setEnabled(false);
            progress.setText("正在结束当前图片，已完成的草稿会保留…");
        });
        root.addView(stopButton);

        LinearLayout tabRow = row();
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i] = button("", () -> { selectedTab = tab; refresh(); });
            addWeighted(tabRow, tabs[i]);
        }
        root.addView(tabRow, vertical(12));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("搜索地点、取件码或快递公司");
        root.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable s) { }
        });
        LinearLayout bulk = row();
        reviewButton = button("核对完整项", this::reviewComplete);
        addWeighted(bulk, reviewButton);
        addWeighted(bulk, button("复制待取总表", () -> copy(Parcel.summary(visible(0, false)))));
        root.addView(bulk, vertical(8));
        list = column();
        root.addView(list, vertical(8));
        root.addView(label("清单只保存在本机。截图识别和通知归纳可能有误，导入后先核对；不会读取短信或同步平台账号。", 12, MUTED, false), vertical(20));
        setContentView(scroll);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        refresh();
        if (savedState != null && savedState.containsKey("edit_code")) {
            Parcel p = new Parcel();
            for (Parcel existing : store.all()) if (existing.id.equals(savedState.getString("edit_id"))) p = existing;
            p.code = savedState.getString("edit_code", "");
            p.station = savedState.getString("edit_station", "");
            p.address = savedState.getString("edit_address", "");
            p.courier = savedState.getString("edit_courier", "");
            edit(p);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        handler.post(poll);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", selectedTab);
        if (editingParcel != null && editorFields != null) {
            state.putString("edit_id", editingParcel.id);
            String[] keys = {"edit_code", "edit_station", "edit_address", "edit_courier"};
            for (int i = 0; i < keys.length; i++) state.putString(keys[i], editorFields[i].getText().toString());
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (dialog != null) dialog.dismiss();
        if (confirmation != null) confirmation.dismiss();
        store.close();
        super.onDestroy();
    }

    private void updateProgress() {
        boolean busy = ParcelImporter.running;
        imagesButton.setEnabled(!busy);
        pasteButton.setEnabled(!busy);
        stopButton.setVisibility(busy ? View.VISIBLE : View.GONE);
        stopButton.setEnabled(!ParcelImporter.cancelRequested);
        progress.setText(ParcelImporter.progress.isEmpty()
                ? getSharedPreferences("parcel_import", MODE_PRIVATE).getString("last_result", "支持货架码、字母码和柜码，也可一次粘贴多条通知。")
                : ParcelImporter.progress);
    }

    private List<Parcel> visible(int tab, boolean filter) {
        String query = filter && search != null ? search.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        List<Parcel> result = new ArrayList<>();
        for (Parcel p : store.all()) {
            boolean matches = tab == 2 ? p.pickedAt > 0 : p.pickedAt == 0 && (tab == 1 ? p.needsReview : !p.needsReview);
            if (matches && (query.isEmpty() || (p.code + " " + p.station + " " + p.address + " " + p.courier)
                    .toLowerCase(Locale.ROOT).contains(query))) result.add(p);
        }
        return result;
    }

    private void refresh() {
        if (list == null || isDestroyed()) return;
        List<Parcel> pending = visible(0, false), review = visible(1, false), done = visible(2, false);
        int locations = 0;
        for (List<Parcel> group : Parcel.groups(pending).values()) {
            Parcel p = group.get(0);
            if (!p.station.isEmpty() || !p.address.isEmpty()) locations++;
        }
        totals.setText("待取 " + pending.size() + " 件 · " + locations + " 个取件点"
                + (review.isEmpty() ? "" : "\n另有 " + review.size() + " 条待核对"));
        int[] counts = {pending.size(), review.size(), done.size()};
        String[] names = {"待取", "待核对", "已取"};
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setText(names[i] + " " + counts[i]);
            tabs[i].setTextColor(i == selectedTab ? Color.WHITE : GREEN);
            tabs[i].setBackground(rounded(i == selectedTab ? GREEN : Color.WHITE));
        }
        reviewButton.setVisibility(selectedTab == 1 ? View.VISIBLE : View.GONE);
        updateProgress();
        list.removeAllViews();
        List<Parcel> shown = visible(selectedTab, true);
        if (shown.isEmpty()) {
            list.addView(label(selectedTab == 1 ? "还没有待核对的通知。导入截图或粘贴文字后，会在这里归纳出每一件。"
                    : selectedTab == 2 ? "取完一件后标记“已取”，记录会保留在这里。"
                    : "这里会把不同平台的快递按取件点归在一起。先导入通知，核对后加入待取。", 16, MUTED, false), vertical(24));
            return;
        }
        for (Map.Entry<String, List<Parcel>> entry : Parcel.groups(shown).entrySet()) {
            List<Parcel> group = entry.getValue();
            Parcel first = group.get(0);
            LinearLayout card = column();
            card.setPadding(dp(16), dp(16), dp(16), dp(10));
            card.setBackground(rounded(Color.WHITE));
            card.addView(label(first.locationLabel() + " · " + group.size() + " 件", 19, INK, true));
            if (!first.address.isEmpty() && !first.address.equals(first.locationLabel())) {
                card.addView(label(first.address, 14, MUTED, false), vertical(4));
            }
            LinearLayout groupActions = row();
            addWeighted(groupActions, button("复制本站清单", () -> copy(Parcel.summary(group,
                    selectedTab == 2 ? "已取" : selectedTab == 1 ? "待核对" : "待取"))));
            addWeighted(groupActions, button("统一地点", () -> editLocation(group)));
            card.addView(groupActions, vertical(4));
            for (Parcel p : group) {
                TextView code = label(p.code.isEmpty() ? "取件码待补充" : p.code, 25, GREEN, true);
                code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                code.setTextIsSelectable(true);
                card.addView(code, vertical(10));
                String detail = (p.courier.isEmpty() ? "快递公司待补充" : p.courier) + " · " + p.source;
                detail += "\n" + (p.pickedAt > 0 ? "已取 " : "导入 ")
                        + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(p.pickedAt > 0 ? p.pickedAt : p.createdAt);
                card.addView(label(detail, 12, MUTED, false), vertical(3));
                if (p.needsReview && !p.warning.isEmpty()) card.addView(label(p.warning, 13, Color.rgb(154, 82, 16), false), vertical(4));
                LinearLayout parcelActions = row();
                addWeighted(parcelActions, button(p.needsReview ? "核对 / 修改" : "详情 / 修改", () -> edit(p)));
                if (!p.code.isEmpty()) addWeighted(parcelActions, button("复制码", () -> copy(p.code)));
                if (!p.needsReview) addWeighted(parcelActions, button(p.pickedAt == 0 ? "已取" : "恢复待取", () -> {
                    p.pickedAt = p.pickedAt == 0 ? System.currentTimeMillis() : 0;
                    save(p);
                }));
                card.addView(parcelActions);
            }
            list.addView(card, vertical(12));
        }
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(intent, PICK_IMAGES); }
        catch (RuntimeException error) { toast("无法打开选图器，可先粘贴通知文字"); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGES || result != RESULT_OK || data == null) return;
        LinkedHashSet<Uri> unique = new LinkedHashSet<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) unique.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) unique.add(data.getData());
        unique.remove(null);
        if (unique.size() > 30) { toast("一次最多选择 30 张，请分批导入"); return; }
        if (!unique.isEmpty()) {
            if (!ParcelImporter.images(this, new ArrayList<>(unique))) toast("上一批正在处理，请稍候");
            selectedTab = 1;
            refresh();
        }
    }

    private void pasteText() {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(10), dp(20), 0);
        content.addView(label("可粘贴多条短信或平台通知。不同通知之间空一行，能更准确区分码与地点。", 14, MUTED, false));
        EditText input = field(content, "通知原文", "", 100000);
        input.setMinLines(5);
        input.setGravity(Gravity.TOP);
        showDialog(new AlertDialog.Builder(this).setTitle("粘贴多条取件通知").setView(content)
                .setPositiveButton("整理到待核对", null).setNegativeButton("取消", null).create());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String text = input.getText().toString();
            if (text.trim().isEmpty()) { input.setError("请粘贴通知内容"); return; }
            if (!ParcelImporter.text(this, text)) { toast("上一批正在处理，请稍候"); return; }
            dialog.dismiss();
            selectedTab = 1;
            refresh();
        });
    }

    private void edit(Parcel parcel) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), dp(12));
        if (!parcel.warning.isEmpty()) content.addView(label(parcel.warning, 14, MUTED, false));
        EditText code = field(content, "完整取件码 / 货架码", parcel.code, 80);
        code.setSingleLine(true);
        AutoCompleteTextView station = new AutoCompleteTextView(this);
        station.setText(parcel.station);
        station.setHint("驿站 / 门店 / 快递柜名称");
        station.setThreshold(1);
        station.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        LinkedHashSet<String> existingNames = new LinkedHashSet<>();
        for (Parcel existing : store.all()) if (!existing.station.isEmpty()) existingNames.add(existing.station);
        station.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(existingNames)));
        content.addView(label("取件点（可沿用已有名称）", 13, MUTED, false), vertical(8));
        content.addView(station);
        EditText address = field(content, "地址 / 位置补充", parcel.address, 200);
        EditText courier = field(content, "快递公司（选填）", parcel.courier, 40);
        if (!parcel.original.isEmpty()) {
            content.addView(label("识别原文 · 请核对码与地点的对应关系", 13, GREEN, true), vertical(16));
            TextView original = label(parcel.original, 14, MUTED, false);
            original.setTextIsSelectable(true);
            content.addView(original, vertical(4));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        showDialog(new AlertDialog.Builder(this).setTitle(parcel.needsReview ? "核对这件快递" : "快递详情")
                .setView(scroll).setPositiveButton(parcel.needsReview ? "加入待取" : "保存", null)
                .setNegativeButton("取消", null).setNeutralButton("删除", null).create());
        editingParcel = parcel;
        editorFields = new EditText[]{code, station, address, courier};
        dialog.setOnDismissListener(ignored -> { editingParcel = null; editorFields = null; });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (code.getText().toString().trim().isEmpty()) { code.setError("请补充完整取件码"); return; }
            if (station.getText().toString().trim().isEmpty() && address.getText().toString().trim().isEmpty()) {
                station.setError("请填写驿站名称或地址，以便按地点归纳"); return;
            }
            parcel.code = Parcel.normalizeCode(code.getText().toString());
            parcel.station = station.getText().toString().trim();
            parcel.address = address.getText().toString().trim();
            parcel.courier = courier.getText().toString().trim();
            parcel.needsReview = false;
            if (parcel.source.isEmpty()) parcel.source = "手动录入";
            for (Parcel existing : store.all()) {
                if (existing.possibleDuplicateOf(parcel)) {
                    confirmation = new AlertDialog.Builder(this).setTitle("同地点已有相同取件码")
                            .setMessage("可能是重复通知，也可能是两件共用一个码。确认仍要保留这件？")
                            .setPositiveButton("仍保留", (d, w) -> { if (save(parcel)) dialog.dismiss(); })
                            .setNegativeButton("返回检查", null).show();
                    return;
                }
            }
            if (save(parcel)) dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> confirmation = new AlertDialog.Builder(this)
                .setTitle("删除这条快递记录？").setMessage("仅删除本机清单，不影响平台订单。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                    try { store.delete(parcel.id); dialog.dismiss(); refresh(); }
                    catch (RuntimeException error) { toast("删除失败，请重试"); }
                }).show());
    }

    private void reviewComplete() {
        List<Parcel> candidates = new ArrayList<>();
        List<Parcel> all = store.all();
        for (Parcel p : visible(1, true)) {
            if (p.code.isEmpty() || (p.station.isEmpty() && p.address.isEmpty()) || !p.warning.isEmpty()) continue;
            boolean duplicate = false;
            for (Parcel other : all) if (other.possibleDuplicateOf(p)) duplicate = true;
            if (!duplicate) candidates.add(p);
        }
        if (candidates.isEmpty()) { toast("没有可批量确认的完整项，请逐件补充或核对冲突"); return; }
        TextView preview = label("请对照通知核对以下归纳：\n\n" + Parcel.summary(candidates), 16, INK, false);
        preview.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView scroll = new ScrollView(this); scroll.addView(preview);
        showDialog(new AlertDialog.Builder(this).setTitle("确认这 " + candidates.size() + " 件")
                .setView(scroll).setNegativeButton("返回逐件修改", null)
                .setPositiveButton("核对无误，加入待取", (d, w) -> {
                    for (Parcel p : candidates) p.needsReview = false;
                    if (saveAll(candidates)) selectedTab = 0;
                    refresh();
                }).create());
    }

    private void editLocation(List<Parcel> group) {
        LinearLayout content = column(); content.setPadding(dp(20), dp(8), dp(20), dp(8));
        content.addView(label("修改当前组的 " + group.size() + " 件。与其他组使用相同名称和地址后，会自动归到一起。", 14, MUTED, false));
        EditText station = field(content, "统一取件点名称", group.get(0).station, 120);
        EditText address = field(content, "统一地址", group.get(0).address, 200);
        showDialog(new AlertDialog.Builder(this).setTitle("整理取件地点").setView(content)
                .setNegativeButton("取消", null).setPositiveButton("保存地点", null).create());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (station.getText().toString().trim().isEmpty() && address.getText().toString().trim().isEmpty()) {
                station.setError("至少填写名称或地址"); return;
            }
            for (Parcel p : group) {
                p.station = station.getText().toString().trim(); p.address = address.getText().toString().trim();
            }
            if (saveAll(group)) dialog.dismiss();
            refresh();
        });
    }

    private boolean save(Parcel parcel) {
        try { store.save(parcel); refresh(); return true; }
        catch (RuntimeException error) { toast("保存失败，请重试"); return false; }
    }

    private boolean saveAll(List<Parcel> parcels) {
        try { store.saveAll(parcels); return true; }
        catch (RuntimeException error) { toast("保存失败，未修改这组记录，请重试"); return false; }
    }

    private void showDialog(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next; dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void copy(String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) { manager.setPrimaryClip(ClipData.newPlainText("取件清单", text)); toast("已复制"); }
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams vertical(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(margin); return p;
    }
    private void addWeighted(LinearLayout parent, View child) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.rightMargin = dp(4); parent.addView(child, p);
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL)); view.setLineSpacing(dp(3), 1); return view;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(GREEN);
        b.setMinHeight(dp(48)); b.setOnClickListener(v -> action.run()); return b;
    }
    private EditText field(LinearLayout parent, String name, String value, int limit) {
        parent.addView(label(name, 13, MUTED, false), vertical(8));
        EditText input = new EditText(this); input.setTextSize(16); input.setText(value); input.setHint(name);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)}); parent.addView(input); return input;
    }
    private GradientDrawable rounded(int color) { GradientDrawable b = new GradientDrawable(); b.setColor(color); b.setCornerRadius(dp(12)); return b; }
}
