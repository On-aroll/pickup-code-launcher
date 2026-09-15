package cn.pickup.launcher;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    static final String ACTION_OPEN = "cn.pickup.launcher.OPEN";
    static final String EXTRA_DESTINATION = "destination";

    private static final int PAGE_BACKGROUND = Color.rgb(247, 248, 244);
    private static final int TEXT_PRIMARY = Color.rgb(27, 29, 27);
    private static final int TEXT_SECONDARY = Color.rgb(91, 96, 91);

    private static final int REQUEST_PICK_CODE_IMAGE = 1;
    private static final AtomicReference<TinyCnn> OCR_MODEL = new AtomicReference<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Destination destination = destinationFromIntent();
        if (destination != null) {
            DeepLinkLauncher.open(this, destination);
            finish();
            return;
        }

        ShortcutSettingsActivity.applyDynamic(this);

        getWindow().setStatusBarColor(PAGE_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildContent());
    }

    private Destination destinationFromIntent() {
        String action = getIntent().getAction();
        if (ACTION_OPEN.equals(action)) {
            return Destination.fromKey(getIntent().getStringExtra(EXTRA_DESTINATION));
        }
        if ("cn.pickup.launcher.OPEN_CAINIAO".equals(action)) {
            return Destination.CAINIAO;
        }
        if ("cn.pickup.launcher.OPEN_CAINIAO_PACKAGES".equals(action)) {
            return Destination.CAINIAO_PACKAGES;
        }
        if ("cn.pickup.launcher.OPEN_TAOBAO".equals(action)) {
            return Destination.TAOBAO;
        }
        if ("cn.pickup.launcher.OPEN_PINDUODUO".equals(action)) {
            return Destination.PINDUODUO;
        }
        if ("cn.pickup.launcher.OPEN_TAOBAO_PENDING".equals(action)) {
            return Destination.TAOBAO_PENDING;
        }
        if ("cn.pickup.launcher.OPEN_PINDUODUO_PENDING".equals(action)) {
            return Destination.PINDUODUO_PENDING;
        }
        if ("cn.pickup.launcher.OPEN_JD".equals(action)) {
            return Destination.JD;
        }
        if ("cn.pickup.launcher.OPEN_XHS".equals(action)) {
            return Destination.XHS;
        }
        if ("cn.pickup.launcher.OPEN_DOUYIN_PENDING".equals(action)) {
            return Destination.DOUYIN_PENDING;
        }
        if ("cn.pickup.launcher.OPEN_KUAISHOU_PENDING".equals(action)) {
            return Destination.KUAISHOU_PENDING;
        }
        if ("cn.pickup.launcher.OPEN_BILIBILI_PENDING".equals(action)) {
            return Destination.BILIBILI_PENDING;
        }
        return null;
    }

    private View buildContent() {
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

        TextView title = text("快递取件2.0", 28, TEXT_PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = verticalParams(0);
        title.setLayoutParams(titleParams);
        root.addView(title);

        TextView author = text("作者：EyanLiu", 13, TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams authorParams = verticalParams(dp(7));
        author.setLayoutParams(authorParams);
        root.addView(author);

        String versionName = "";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        TextView versionView = text("版本 " + versionName, 12, TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams versionParams = verticalParams(dp(3));
        versionView.setLayoutParams(versionParams);
        root.addView(versionView);

        TextView description = text("快速打开取件码，也能查看各平台的待取快递。", 15, TEXT_SECONDARY, Typeface.NORMAL);
        description.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams descriptionParams = verticalParams(dp(14));
        descriptionParams.bottomMargin = dp(26);
        description.setLayoutParams(descriptionParams);
        root.addView(description);
        root.addView(ocrEntry());

        for (String key : ShortcutSettingsActivity.loadOrder(this)) {
            if (ShortcutSettingsActivity.KEY_OPEN_APP.equals(key)) {
                continue;
            }
            Destination destination = Destination.fromKey(key);
            if (destination != null) {
                addEntryRow(root, destination);
            }
        }

        TextView shortcutTitle = text("添加到桌面", 17, TEXT_PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams shortcutTitleParams = verticalParams(dp(18));
        shortcutTitleParams.bottomMargin = dp(10);
        shortcutTitle.setLayoutParams(shortcutTitleParams);
        root.addView(shortcutTitle);

        TextView shortcutDescription = text(
                "添加后不必进入本应用，桌面点击一次即可跳转。",
                14,
                TEXT_SECONDARY,
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams shortcutDescriptionParams = verticalParams(0);
        shortcutDescriptionParams.bottomMargin = dp(12);
        shortcutDescription.setLayoutParams(shortcutDescriptionParams);
        root.addView(shortcutDescription);

        LinearLayout shortcutButtons = new LinearLayout(this);
        shortcutButtons.setOrientation(LinearLayout.HORIZONTAL);
        java.util.List<Destination> codeEntries = new java.util.ArrayList<>();
        java.util.List<Destination> pendingEntries = new java.util.ArrayList<>();
        for (String key : ShortcutSettingsActivity.loadOrder(this)) {
            if (ShortcutSettingsActivity.KEY_OPEN_APP.equals(key)) {
                continue;
            }
            Destination destination = Destination.fromKey(key);
            if (destination == null) {
                continue;
            }
            switch (destination) {
                case CAINIAO:
                case TAOBAO:
                case PINDUODUO:
                    codeEntries.add(destination);
                    break;
                default:
                    pendingEntries.add(destination);
                    break;
            }
        }
        shortcutButtons.setWeightSum(codeEntries.size());
        for (Destination destination : codeEntries) {
            addPinButton(shortcutButtons, destination, shortLabel(destination));
        }
        root.addView(shortcutButtons);

        LinearLayout pendingShortcutButtons = new LinearLayout(this);
        pendingShortcutButtons.setOrientation(LinearLayout.HORIZONTAL);
        pendingShortcutButtons.setWeightSum(pendingEntries.size());
        LinearLayout.LayoutParams pendingShortcutParams = verticalParams(dp(8));
        pendingShortcutButtons.setLayoutParams(pendingShortcutParams);
        for (Destination destination : pendingEntries) {
            addPinButton(pendingShortcutButtons, destination, shortLabel(destination));
        }
        root.addView(pendingShortcutButtons);

        TextView customTitle = text("入口自定义", 17, TEXT_PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams customTitleParams = verticalParams(dp(18));
        customTitleParams.bottomMargin = dp(10);
        customTitle.setLayoutParams(customTitleParams);
        root.addView(customTitle);

        TextView customDescription = text(
                "调整入口顺序与长按菜单显示项。",
                14,
                TEXT_SECONDARY,
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams customDescriptionParams = verticalParams(0);
        customDescriptionParams.bottomMargin = dp(12);
        customDescription.setLayoutParams(customDescriptionParams);
        root.addView(customDescription);

        TextView customButton = text("自定义入口", 14, TEXT_PRIMARY, Typeface.BOLD);
        customButton.setGravity(Gravity.CENTER);
        customButton.setBackground(rounded(Color.WHITE, 8));
        customButton.setClickable(true);
        customButton.setFocusable(true);
        customButton.setContentDescription("自定义入口顺序与长按菜单");
        customButton.setOnClickListener(view ->
                startActivity(new android.content.Intent(this, ShortcutSettingsActivity.class)));
        root.addView(customButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        return scrollView;
    }

    private View ocrEntry() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(14), dp(15));
        row.setMinimumHeight(dp(82));
        row.setBackground(rounded(Color.rgb(235, 242, 250), 8));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription("从截图识别取件码");
        row.setOnClickListener(view -> pickCodeImage());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);

        TextView mark = text("码", 18, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(Color.rgb(37, 99, 235), 7));
        row.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(10), 0);

        TextView titleView = text("识别取件码", 17, TEXT_PRIMARY, Typeface.BOLD);
        labels.addView(titleView);

        TextView subtitleView = text("从截图识别取件码，复制或直达取件", 13, TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = verticalParams(dp(3));
        subtitleView.setLayoutParams(subtitleParams);
        labels.addView(subtitleView);

        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView arrow = text("›", 28, Color.rgb(37, 99, 235), Typeface.NORMAL);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(44)));

        return row;
    }

    private void pickCodeImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_CODE_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_CODE_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Toast.makeText(this, "识别中…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                Bitmap bmp = decodeSampled(uri);
                if (bmp == null) {
                    mainHandler.post(() -> Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show());
                    return;
                }
                TinyCnn model = ocrModel();
                if (model == null) {
                    bmp.recycle();
                    mainHandler.post(() -> Toast.makeText(this, "模型加载失败", Toast.LENGTH_SHORT).show());
                    return;
                }
                PickupOcr.Result result = PickupOcr.recognize(bmp, model);
                bmp.recycle();
                mainHandler.post(() -> showOcrResult(result));
            }).start();
        }
    }

    private TinyCnn ocrModel() {
        TinyCnn model = OCR_MODEL.get();
        if (model != null) {
            return model;
        }
        synchronized (OCR_MODEL) {
            model = OCR_MODEL.get();
            if (model != null) {
                return model;
            }
            try (InputStream in = getAssets().open("pickup_ocr.bin")) {
                model = TinyCnn.load(in);
                OCR_MODEL.set(model);
                return model;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private Bitmap decodeSampled(Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception e) {
            return null;
        }
        int sample = 1;
        int edge = Math.max(bounds.outWidth, bounds.outHeight);
        while (edge / (sample * 2) >= 4096) {
            sample *= 2;
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private void showOcrResult(PickupOcr.Result result) {
        if (result == null || result.code == null || result.code.isEmpty()) {
            Toast.makeText(this, "未识别到取件码：请截取包含取件码的清晰截图", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(8));

        TextView codeView = new TextView(this);
        codeView.setText(result.code);
        codeView.setTextSize(52);
        codeView.setTextColor(Color.rgb(20, 30, 25));
        codeView.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        codeView.setGravity(Gravity.CENTER);
        codeView.setLetterSpacing(0.25f);
        content.addView(codeView);

        StringBuilder confText = new StringBuilder("置信度 ");
        for (float c : result.confidences) {
            confText.append(String.format(java.util.Locale.US, "%.0f%% ", c * 100f));
        }
        TextView confView = new TextView(this);
        confView.setText(confText.toString().trim());
        confView.setTextSize(13);
        confView.setTextColor(TEXT_SECONDARY);
        confView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams confParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        confParams.topMargin = dp(6);
        confView.setLayoutParams(confParams);
        content.addView(confView);

        TextView hint = new TextView(this);
        hint.setText("长按取件码可复制");
        hint.setTextSize(12);
        hint.setTextColor(TEXT_SECONDARY);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(10);
        hint.setLayoutParams(hintParams);
        content.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle("取件码识别")
                .setView(content)
                .setPositiveButton("复制", (dialog, which) -> copyCode(result.code))
                .setNegativeButton("去菜鸟取件", (dialog, which) ->
                        DeepLinkLauncher.open(this, Destination.CAINIAO))
                .setNeutralButton("关闭", null)
                .show();
    }

    private void copyCode(String code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("取件码", code));
            Toast.makeText(this, "取件码已复制：" + code, Toast.LENGTH_SHORT).show();
        }
    }

    private void addEntryRow(LinearLayout root, Destination destination) {
        switch (destination) {
            case CAINIAO:
                root.addView(serviceRow(
                        destination,
                        "菜鸟取件",
                        "打开菜鸟身份码，失败时进入菜鸟 App",
                        Color.rgb(231, 244, 235),
                        Color.rgb(20, 120, 72)
                ));
                break;
            case CAINIAO_PACKAGES:
                root.addView(serviceRow(
                        destination,
                        "菜鸟包裹",
                        "打开菜鸟包裹列表",
                        Color.rgb(231, 244, 235),
                        Color.rgb(20, 120, 72)
                ));
                break;
            case TAOBAO:
                root.addView(serviceRow(
                        destination,
                        "淘宝取件",
                        "打开淘宝末端取件身份码",
                        Color.rgb(255, 240, 227),
                        Color.rgb(194, 75, 18)
                ));
                break;
            case TAOBAO_PENDING:
                root.addView(serviceRow(
                        destination,
                        "淘宝订单",
                        "打开淘宝订单列表",
                        Color.rgb(255, 240, 227),
                        Color.rgb(194, 75, 18)
                ));
                break;
            case PINDUODUO:
                root.addView(serviceRow(
                        destination,
                        "拼多多取件",
                        "打开多多买菜取件身份码",
                        Color.rgb(255, 232, 236),
                        Color.rgb(190, 35, 60)
                ));
                break;
            case PINDUODUO_PENDING:
                root.addView(serviceRow(
                        destination,
                        "拼多多待取",
                        "打开拼多多待取与收货列表",
                        Color.rgb(255, 232, 236),
                        Color.rgb(190, 35, 60)
                ));
                break;
            case JD:
                root.addView(serviceRow(
                        destination,
                        "京东待取快递",
                        "打开京东订单列表",
                        Color.rgb(255, 239, 214),
                        Color.rgb(180, 93, 13)
                ));
                break;
            case XHS:
                root.addView(serviceRow(
                        destination,
                        "小红书待取快递",
                        "打开小红书订单列表",
                        Color.rgb(255, 232, 238),
                        Color.rgb(204, 63, 103)
                ));
                break;
            case DOUYIN_PENDING:
                root.addView(serviceRow(
                        destination,
                        "抖音订单",
                        "打开抖音商城订单列表",
                        Color.rgb(240, 240, 242),
                        Color.rgb(22, 24, 35)
                ));
                break;
            case KUAISHOU_PENDING:
                root.addView(serviceRow(
                        destination,
                        "快手订单",
                        "打开快手小店订单列表",
                        Color.rgb(255, 237, 233),
                        Color.rgb(255, 73, 6)
                ));
                break;
            case BILIBILI_PENDING:
                root.addView(serviceRow(
                        destination,
                        "哔哩哔哩订单",
                        "打开会员购订单与待收货",
                        Color.rgb(255, 240, 246),
                        Color.rgb(251, 114, 153)
                ));
                break;
        }
    }

    private String shortLabel(Destination destination) {
        switch (destination) {
            case CAINIAO:
                return "菜鸟码";
            case CAINIAO_PACKAGES:
                return "菜鸟包裹";
            case TAOBAO:
                return "淘宝码";
            case TAOBAO_PENDING:
                return "淘宝订单";
            case PINDUODUO:
                return "拼多多码";
            case PINDUODUO_PENDING:
                return "拼多多待取";
            case JD:
                return "京东待取";
            case XHS:
                return "小红书待取";
            case DOUYIN_PENDING:
                return "抖音订单";
            case KUAISHOU_PENDING:
                return "快手订单";
            case BILIBILI_PENDING:
                return "哔哩订单";
        }
        return destination.title;
    }

    private View serviceRow(
            Destination destination,
            String title,
            String subtitle,
            int backgroundColor,
            int accentColor
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(14), dp(15));
        row.setMinimumHeight(dp(82));
        row.setBackground(rounded(backgroundColor, 8));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription("打开" + destination.title);
        row.setOnClickListener(view -> DeepLinkLauncher.open(this, destination));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);

        TextView mark = text(destination.mark, 18, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(accentColor, 7));
        row.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(10), 0);

        TextView titleView = text(title, 17, TEXT_PRIMARY, Typeface.BOLD);
        labels.addView(titleView);

        TextView subtitleView = text(subtitle, 13, TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = verticalParams(dp(3));
        subtitleView.setLayoutParams(subtitleParams);
        labels.addView(subtitleView);

        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView arrow = text("›", 28, accentColor, Typeface.NORMAL);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(44)));

        return row;
    }

    private void addPinButton(
            LinearLayout parent,
            Destination destination,
            String label
    ) {
        TextView button = text(label, 14, TEXT_PRIMARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.WHITE, 8));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("将" + destination.title + "添加到桌面");
        button.setOnClickListener(view -> ShortcutPinning.request(this, destination));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        if (parent.getChildCount() > 0) {
            params.leftMargin = dp(8);
        }
        parent.addView(button, params);
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

    private LinearLayout.LayoutParams verticalParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topMargin;
        return params;
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
