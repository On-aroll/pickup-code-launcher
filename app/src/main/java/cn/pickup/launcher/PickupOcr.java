package cn.pickup.launcher;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * On-device pickup-code OCR pipeline (no OpenCV / ML Kit).
 *
 *   grayscale -> 3x3 median -> Otsu binarization -> connected components
 *   -> merge split strokes -> row clustering -> best numeric window -> CNN digits
 *
 * The pipeline mirrors ocr-model/verify_pipeline.py which reports 99.75%
 * full-string accuracy on synthetic screenshots.
 */
public final class PickupOcr {

    private static final int MAX_EDGE = 2400;
    private static final int MIN_BLOB_AREA = 12;
    private static final float MERGE_GAP_RATIO = 0.55f;
    private static final float MERGE_OVERLAP_RATIO = 0.5f;

    /** min / max character height accepted as a digit. */
    private static final int MIN_CHAR_H = 14;
    private static final int MAX_CHAR_H = 800;

    private PickupOcr() {
    }

    public static final class Result {
        public final String code;
        public final float[] confidences;
        public final int boxCount;

        Result(String code, float[] confidences, int boxCount) {
            this.code = code;
            this.confidences = confidences;
            this.boxCount = boxCount;
        }
    }

    public static Result recognize(Bitmap source, TinyCnn cnn) {
        Bitmap bmp = source;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (Math.max(w, h) > MAX_EDGE) {
            float scale = (float) MAX_EDGE / Math.max(w, h);
            bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * scale)),
                    Math.max(1, Math.round(h * scale)), true);
            w = bmp.getWidth();
            h = bmp.getHeight();
        }

        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        if (bmp != source) {
            bmp.recycle();
        }

        byte[] gray = toGray(pixels, w, h);
        median3(gray, w, h);
        byte[] bin = otsu(gray, w, h);
        if (darkRatio(bin, w, h) > 0.45f) { // dark-mode screenshot: invert
            for (int i = 0; i < bin.length; i++) {
                bin[i] = (byte) (255 - (bin[i] & 0xFF));
            }
        }

        List<int[]> boxes = connectedComponents(bin, w, h);
        List<int[]> merged = mergeBoxes(boxes);

        List<List<int[]>> rows = clusterRows(merged);
        List<int[]> best = bestNumericWindow(rows, cnn, bin);
        if (best == null) {
            return new Result("", new float[0], merged.size());
        }

        StringBuilder sb = new StringBuilder();
        float[] conf = new float[best.size()];
        for (int i = 0; i < best.size(); i++) {
            float[][] vec = to28(bin, w, h, best.get(i));
            TinyCnn.Result r = cnn.predict(vec);
            sb.append(r.digit);
            conf[i] = r.confidence;
        }
        return new Result(sb.toString(), conf, merged.size());
    }

    // ---------- image preprocessing ----------

    private static byte[] toGray(int[] pixels, int w, int h) {
        byte[] out = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            out[i] = (byte) ((r * 299 + g * 587 + b * 114) / 1000);
        }
        return out;
    }

    private static void median3(byte[] src, int w, int h) {
        byte[] dst = new byte[src.length];
        int[] win = new int[9];
        for (int y = 0; y < h; y++) {
            int rowBase = y * w;
            for (int x = 0; x < w; x++) {
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    int base = yy * w;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        win[n++] = src[base + xx] & 0xFF;
                    }
                }
                if (n < 9) { // border: keep original
                    dst[rowBase + x] = src[rowBase + x];
                    continue;
                }
                Arrays.sort(win, 0, n);
                dst[rowBase + x] = (byte) win[n / 2];
            }
        }
        System.arraycopy(dst, 0, src, 0, src.length);
    }

    private static byte[] otsu(byte[] gray, int w, int h) {
        int[] hist = new int[256];
        for (byte b : gray) {
            hist[b & 0xFF]++;
        }
        int total = w * h;
        float sum = 0f;
        for (int i = 0; i < 256; i++) {
            sum += i * hist[i];
        }
        float sumB = 0f;
        int wB = 0;
        float maxVar = -1f;
        int threshold = 128;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += t * hist[t];
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            float between = (float) wB * wF * (mB - mF) * (mB - mF);
            if (between > maxVar) {
                maxVar = between;
                threshold = t;
            }
        }
        byte[] out = new byte[gray.length];
        for (int i = 0; i < gray.length; i++) {
            out[i] = (gray[i] & 0xFF) < threshold ? (byte) 0 : (byte) 255;
        }
        return out;
    }

    private static float darkRatio(byte[] bin, int w, int h) {
        int dark = 0;
        for (byte b : bin) {
            if ((b & 0xFF) == 0) dark++;
        }
        return (float) dark / (w * h);
    }

    // ---------- segmentation ----------

    /** 4-connected components; returns boxes {top, x0, x1, bottom}. */
    private static List<int[]> connectedComponents(byte[] bin, int w, int h) {
        byte[] visited = new byte[bin.length];
        int[] queue = new int[bin.length];
        List<int[]> boxes = new ArrayList<>();
        for (int start = 0; start < bin.length; start++) {
            if ((bin[start] & 0xFF) != 0 || visited[start] != 0) continue;
            int qHead = 0, qTail = 0;
            queue[qTail++] = start;
            visited[start] = 1;
            int minY = h, maxY = -1, minX = w, maxX = -1, area = 0;
            while (qHead < qTail) {
                int p = queue[qHead++];
                int x = p % w, y = p / w;
                area++;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                int[] nb = new int[4];
                int nn = 0;
                if (x > 0) nb[nn++] = p - 1;
                if (x < w - 1) nb[nn++] = p + 1;
                if (y > 0) nb[nn++] = p - w;
                if (y < h - 1) nb[nn++] = p + w;
                for (int k = 0; k < nn; k++) {
                    int q = nb[k];
                    if ((bin[q] & 0xFF) == 0 && visited[q] == 0) {
                        visited[q] = 1;
                        queue[qTail++] = q;
                    }
                }
            }
            if (area >= MIN_BLOB_AREA) {
                int ch = maxY - minY + 1;
                int cw = maxX - minX + 1;
                if (ch >= MIN_CHAR_H && ch <= MAX_CHAR_H) {
                    boxes.add(new int[]{minY, minX, maxX + 1, maxY + 1});
                }
            }
        }
        return boxes;
    }

    /** Merge vertical fragments of the same glyph (e.g. "4" made of two strokes). */
    private static List<int[]> mergeBoxes(List<int[]> boxes) {
        List<int[]> list = new ArrayList<>(boxes);
        list.sort(Comparator.comparingInt(b -> b[1]));
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < list.size(); i++) {
                int[] a = list.get(i);
                for (int j = i + 1; j < list.size(); j++) {
                    int[] b = list.get(j);
                    int aw = a[2] - a[1], bw = b[2] - b[1];
                    int ah = a[3] - a[0], bh = b[3] - b[0];
                    int gap = b[1] - a[2];
                    int overlap = Math.min(a[3], b[3]) - Math.max(a[0], b[0]);
                    boolean horizontalNear = gap < Math.max(aw, bw) * MERGE_GAP_RATIO;
                    boolean verticalOverlap = overlap > Math.min(ah, bh) * MERGE_OVERLAP_RATIO;
                    if (horizontalNear && verticalOverlap) {
                        a[0] = Math.min(a[0], b[0]);
                        a[3] = Math.max(a[3], b[3]);
                        a[1] = Math.min(a[1], b[1]);
                        a[2] = Math.max(a[2], b[2]);
                        list.remove(j);
                        changed = true;
                        break;
                    }
                }
                if (changed) break;
            }
        } while (changed);
        return list;
    }

    /** Group boxes into text lines by vertical overlap. */
    private static List<List<int[]>> clusterRows(List<int[]> boxes) {
        List<List<int[]>> rows = new ArrayList<>();
        List<int[]> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingInt(b -> b[0]));
        for (int[] box : sorted) {
            boolean placed = false;
            for (List<int[]> row : rows) {
                int minTop = Integer.MAX_VALUE, maxBottom = Integer.MIN_VALUE, sumH = 0;
                for (int[] b : row) {
                    minTop = Math.min(minTop, b[0]);
                    maxBottom = Math.max(maxBottom, b[3]);
                    sumH += b[3] - b[0];
                }
                int avgH = sumH / row.size();
                int overlap = Math.min(maxBottom, box[3]) - Math.max(minTop, box[0]);
                if (overlap > avgH * 0.3f) {
                    row.add(box);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                List<int[]> row = new ArrayList<>();
                row.add(box);
                rows.add(row);
            }
        }
        for (List<int[]> row : rows) {
            row.sort(Comparator.comparingInt(b -> b[1]));
        }
        return rows;
    }

    private static final class Window {
        List<int[]> boxes = new ArrayList<>();
        float avgConf = 0f;
        float widthSpread = 0f;
        float gapSpread = 0f;
        float score = -1f;
    }

    /** Pick the 4-8 consecutive digits that look like a pickup code. */
    private static List<int[]> bestNumericWindow(List<List<int[]>> rows, TinyCnn cnn, byte[] bin) {
        Window best = new Window();
        for (List<int[]> row : rows) {
            if (row.size() < 4) continue;
            int n = row.size();
            TinyCnn.Result[] res = new TinyCnn.Result[n];
            for (int i = 0; i < n; i++) {
                res[i] = cnn.predict(to28(bin, 0, 0, row.get(i)));
            }
            for (int start = 0; start < n; start++) {
                for (int len = 4; len <= 8 && start + len <= n; len++) {
                    List<int[]> sub = row.subList(start, start + len);
                    float sumConf = 0f;
                    int minW = Integer.MAX_VALUE, maxW = 0;
                    int minGap = Integer.MAX_VALUE, maxGap = 0;
                    for (int i = 0; i < len; i++) {
                        int[] b = sub.get(i);
                        int bw = b[2] - b[1];
                        minW = Math.min(minW, bw);
                        maxW = Math.max(maxW, bw);
                        sumConf += res[start + i].confidence;
                        if (i > 0) {
                            int gap = b[1] - sub.get(i - 1)[2];
                            minGap = Math.min(minGap, gap);
                            maxGap = Math.max(maxGap, gap);
                        }
                    }
                    float avgConf = sumConf / len;
                    float widthSpread = maxW == 0 ? 1f : (float) (maxW - minW) / maxW;
                    float gapSpread = maxGap <= 0 ? 0f : (float) (maxGap - minGap) / Math.max(maxGap, 1);
                    // digits: uniform width + even gaps + high confidence
                    float score = avgConf - widthSpread * 0.5f - gapSpread * 0.8f;
                    if (score > best.score && avgConf >= 0.55f && widthSpread < 0.75f && gapSpread < 1.2f) {
                        best.score = score;
                        best.avgConf = avgConf;
                        best.widthSpread = widthSpread;
                        best.gapSpread = gapSpread;
                        best.boxes = new ArrayList<>(sub);
                    }
                }
            }
        }
        return best.boxes.isEmpty() ? null : best.boxes;
    }

    /** Tight crop -> center-pad to square -> 28x28 -> [0,1] digit=1. */
    private static float[][] to28(byte[] bin, int imgW, int imgH, int[] box) {
        int top = box[0], x0 = box[1], x1 = box[2], bottom = box[3];
        int cw = x1 - x0, ch = bottom - top;
        int side = Math.max(cw, ch) + 1;
        int yOff = (side - ch) / 2;
        int xOff = (side - cw) / 2;
        float[][] out = new float[28][28];
        for (int dy = 0; dy < 28; dy++) {
            float sy = (dy + 0.5f) * side / 28f - 0.5f;
            for (int dx = 0; dx < 28; dx++) {
                float sx = (dx + 0.5f) * side / 28f - 0.5f;
                int y0 = (int) Math.floor(sy);
                int x0i = (int) Math.floor(sx);
                float fy = sy - y0;
                float fx = sx - x0i;
                float v = 0f;
                for (int ky = 0; ky < 2; ky++) {
                    for (int kx = 0; kx < 2; kx++) {
                        int py = y0 + ky - yOff;
                        int px = x0i + kx - xOff;
                        int val = 255;
                        if (py >= 0 && py < ch && px >= 0 && px < cw) {
                            val = bin[(top + py) * imgW + (x0 + px)] & 0xFF;
                        }
                        float wy = ky == 0 ? (1 - fy) : fy;
                        float wx = kx == 0 ? (1 - fx) : fx;
                        v += val * wy * wx;
                    }
                }
                out[dy][dx] = (255f - v) / 255f;
            }
        }
        return out;
    }
}
