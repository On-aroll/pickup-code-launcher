package cn.pickup.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** One application-scoped batch. Completed drafts survive screen rotations and app restarts. */
final class ParcelImporter {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    static volatile boolean running;
    static volatile boolean cancelRequested;
    static volatile String progress = "";
    static volatile long revision;

    static synchronized boolean images(Context context, List<Uri> uris) {
        if (running) return false;
        running = true;
        cancelRequested = false;
        progress = "准备识别 " + uris.size() + " 张截图…";
        Context app = context.getApplicationContext();
        List<Uri> inputs = new ArrayList<>(uris);
        WORKER.execute(() -> {
            int added = 0, duplicates = 0;
            List<Integer> failures = new ArrayList<>();
            TextRecognizer recognizer = null;
            try (ParcelStore store = new ParcelStore(app)) {
                recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                for (int i = 0; i < inputs.size() && !cancelRequested; i++) {
                    progress = "正在识别 " + (i + 1) + "/" + inputs.size() + " 张 · 已整理 " + added + " 条";
                    Bitmap bitmap = null;
                    try {
                        bitmap = decode(app, inputs.get(i));
                        if (bitmap == null) throw new IllegalArgumentException("Unreadable image");
                        int rotation = rotation(app, inputs.get(i));
                        String text = recognize(recognizer, bitmap, rotation);
                        if (cancelRequested) break;
                        if (text.trim().isEmpty()) throw new IllegalArgumentException("No text");
                        for (Parcel parcel : ParcelParser.parse(text, "截图 " + (i + 1))) {
                            if (store.importDraft(parcel)) added++; else duplicates++;
                        }
                        revision++;
                    } catch (Exception | OutOfMemoryError error) {
                        failures.add(i + 1);
                    } finally {
                        if (bitmap != null) bitmap.recycle();
                    }
                }
            } catch (Exception | OutOfMemoryError error) {
                progress = "识别暂不可用，可先粘贴通知文字整理；已完成的草稿已保存";
                finish(app);
                return;
            } finally {
                if (recognizer != null) recognizer.close();
            }
            progress = (cancelRequested ? "已停止。" : "整理完成。") + "新增 " + added + " 条待核对，跳过 "
                    + duplicates + " 条完全重复通知"
                    + (failures.isEmpty() ? "" : "；第 " + failures + " 张读取失败或没有文字，请重选或粘贴文字");
            finish(app);
        });
        return true;
    }

    static synchronized boolean text(Context context, String input) {
        if (running) return false;
        running = true;
        cancelRequested = false;
        progress = "正在归纳通知…";
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            int added = 0, duplicates = 0;
            try (ParcelStore store = new ParcelStore(app)) {
                for (Parcel parcel : ParcelParser.parse(input, "粘贴通知")) {
                    if (cancelRequested) break;
                    if (store.importDraft(parcel)) added++; else duplicates++;
                }
                progress = "新增 " + added + " 条待核对，跳过 " + duplicates + " 条完全重复通知";
            } catch (RuntimeException error) {
                progress = "保存失败，请保留原通知后重试";
            }
            finish(app);
        });
        return true;
    }

    private static void finish(Context context) {
        context.getSharedPreferences("parcel_import", Context.MODE_PRIVATE).edit().putString("last_result", progress).apply();
        revision++;
        running = false;
    }

    private static Bitmap decode(Context context, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / sample > 1800
                || (long) bounds.outWidth * bounds.outHeight / ((long) sample * sample) > 6_000_000) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    private static int rotation(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT < 24) return 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            android.media.ExifInterface exif = new android.media.ExifInterface(in);
            int value = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            if (value == 6) return 90;
            if (value == 3) return 180;
            if (value == 8) return 270;
        } catch (Exception ignored) { }
        return 0;
    }

    private static final class Line {
        final String text;
        final Rect box;
        Line(String text, Rect box) { this.text = text; this.box = box; }
    }

    private static String recognize(TextRecognizer recognizer, Bitmap bitmap, int rotation) throws Exception {
        // Keep long screenshots legible by processing overlapping strips instead of shrinking to a tiny height.
        if (rotation != 0) return Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, rotation))).getText();
        List<Line> lines = new ArrayList<>();
        for (int top = 0; top < bitmap.getHeight() && !cancelRequested; top += 1640) {
            int height = Math.min(1800, bitmap.getHeight() - top);
            Bitmap tile = Bitmap.createBitmap(bitmap, 0, top, bitmap.getWidth(), height);
            try {
                Text result = Tasks.await(recognizer.process(InputImage.fromBitmap(tile, 0)));
                for (Text.TextBlock block : result.getTextBlocks()) {
                    for (Text.Line textLine : block.getLines()) {
                        Rect box = textLine.getBoundingBox();
                        if (box == null) continue;
                        box = new Rect(box);
                        box.offset(0, top);
                        boolean duplicate = false;
                        for (Line existing : lines) {
                            if (existing.text.equals(textLine.getText()) && Rect.intersects(existing.box, box)) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) lines.add(new Line(textLine.getText(), box));
                    }
                }
            } finally {
                if (tile != bitmap) tile.recycle();
            }
            if (top + height == bitmap.getHeight()) break;
        }
        Collections.sort(lines, (a, b) -> a.box.top == b.box.top
                ? Integer.compare(a.box.left, b.box.left) : Integer.compare(a.box.top, b.box.top));
        StringBuilder out = new StringBuilder();
        Line previous = null;
        for (Line line : lines) {
            if (previous != null) {
                out.append('\n');
            }
            out.append(line.text);
            previous = line;
        }
        return out.toString();
    }
}
