package cn.pickup.launcher;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tiny CNN for single-digit classification (28x28 grayscale, digit=1).
 * Architecture matches ocr-model/train.py:
 *   conv3x3(1->8, pad1) + ReLU + pool2
 *   conv3x3(8->16, pad1) + ReLU + pool2
 *   fc(784->128) + ReLU
 *   fc(128->10) -> softmax
 *
 * Pure Java forward pass, no external dependencies. Weights come from a flat
 * float32 binary in assets (see ocr-model/export_weights.py).
 */
public final class TinyCnn {

    private static final int IN = 28;
    private static final int C1 = 8;
    private static final int C2 = 16;
    private static final int HIDDEN = 128;

    private final float[][] conv1W; // [C1][9]
    private final float[] conv1B;   // [C1]
    private final float[][] conv2W; // [C2][C1*9]
    private final float[] conv2B;   // [C2]
    private final float[][] fc1W;   // [HIDDEN][784]
    private final float[] fc1B;     // [HIDDEN]
    private final float[][] fc2W;   // [10][HIDDEN]
    private final float[] fc2B;     // [10]

    private TinyCnn(float[][] conv1W, float[] conv1B, float[][] conv2W, float[] conv2B,
                    float[][] fc1W, float[] fc1B, float[][] fc2W, float[] fc2B) {
        this.conv1W = conv1W;
        this.conv1B = conv1B;
        this.conv2W = conv2W;
        this.conv2B = conv2B;
        this.fc1W = fc1W;
        this.fc1B = fc1B;
        this.fc2W = fc2W;
        this.fc2B = fc2B;
    }

    /** Load weights from the exported flat binary. */
    public static TinyCnn load(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        float[][] conv1W = read(dis, C1, 9);
        float[] conv1B = read(dis, C1);
        float[][] conv2W = read(dis, C2, C1 * 9);
        float[] conv2B = read(dis, C2);
        float[][] fc1W = read(dis, HIDDEN, 28 * 28);
        float[] fc1B = read(dis, HIDDEN);
        float[][] fc2W = read(dis, 10, HIDDEN);
        float[] fc2B = read(dis, 10);
        return new TinyCnn(conv1W, conv1B, conv2W, conv2B, fc1W, fc1B, fc2W, fc2B);
    }

    private static float[] read(DataInputStream dis, int n) throws IOException {
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = dis.readFloat();
        }
        return out;
    }

    private static float[][] read(DataInputStream dis, int rows, int cols) throws IOException {
        float[][] out = new float[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out[r][c] = dis.readFloat();
            }
        }
        return out;
    }

    /** Predict digit 0-9 and confidence. Input is 28x28 in [0,1], digit=1. */
    public Result predict(float[][] input) {
        float[] probs = distribution(input);
        int best = 0;
        float bestP = 0f;
        for (int i = 0; i < 10; i++) {
            if (probs[i] > bestP) {
                bestP = probs[i];
                best = i;
            }
        }
        return new Result(best, bestP);
    }

    /** Full softmax distribution over digits 0-9. */
    public float[] distribution(float[][] input) {
        // conv1 -> relu -> pool(max over 2x2) -> 14x14x8
        float[] c1 = new float[14 * 14 * C1];
        for (int oc = 0; oc < C1; oc++) {
            float[] w = conv1W[oc];
            float bias = conv1B[oc];
            for (int oy = 0; oy < 14; oy++) {
                for (int ox = 0; ox < 14; ox++) {
                    float best = 0f; // ReLU output >= 0, so window max is >= 0
                    for (int dy = 0; dy < 2; dy++) {
                        int cy = oy * 2 + dy;
                        for (int dx = 0; dx < 2; dx++) {
                            int cx = ox * 2 + dx;
                            float sum = bias;
                            for (int ky = 0; ky < 3; ky++) {
                                int iy = cy + ky - 1;
                                if (iy < 0 || iy >= IN) continue;
                                for (int kx = 0; kx < 3; kx++) {
                                    int ix = cx + kx - 1;
                                    if (ix < 0 || ix >= IN) continue;
                                    sum += input[iy][ix] * w[ky * 3 + kx];
                                }
                            }
                            if (sum > best) best = sum;
                        }
                    }
                    c1[(oy * 14 + ox) * C1 + oc] = best;
                }
            }
        }

        // conv2 -> relu -> pool(max over 2x2) -> 7x7x16, layout [C2][49] (channel-major, matches PyTorch flatten)
        float[] c2 = new float[C2 * 49];
        for (int oc = 0; oc < C2; oc++) {
            float[] w = conv2W[oc];
            float bias = conv2B[oc];
            int outBase = oc * 49;
            for (int oy = 0; oy < 7; oy++) {
                for (int ox = 0; ox < 7; ox++) {
                    float best = 0f;
                    for (int dy = 0; dy < 2; dy++) {
                        int cy = oy * 2 + dy;
                        for (int dx = 0; dx < 2; dx++) {
                            int cx = ox * 2 + dx;
                            float sum = bias;
                            for (int ic = 0; ic < C1; ic++) {
                                for (int ky = 0; ky < 3; ky++) {
                                    int iy = cy + ky - 1;
                                    if (iy < 0 || iy >= 14) continue;
                                    for (int kx = 0; kx < 3; kx++) {
                                        int ix = cx + kx - 1;
                                        if (ix < 0 || ix >= 14) continue;
                                        float v = c1[(iy * 14 + ix) * C1 + ic];
                                        if (v != 0f) {
                                            sum += v * w[(ic * 3 + ky) * 3 + kx];
                                        }
                                    }
                                }
                            }
                            if (sum > best) best = sum;
                        }
                    }
                    c2[outBase + oy * 7 + ox] = best;
                }
            }
        }

        // fc1 + relu
        float[] h = new float[HIDDEN];
        for (int i = 0; i < HIDDEN; i++) {
            float sum = fc1B[i];
            float[] w = fc1W[i];
            for (int j = 0; j < 784; j++) {
                float v = c2[j];
                if (v != 0f) {
                    sum += v * w[j];
                }
            }
            h[i] = sum > 0 ? sum : 0f;
        }

        // fc2 + softmax
        float[] logits = new float[10];
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 10; i++) {
            float sum = fc2B[i];
            float[] w = fc2W[i];
            for (int j = 0; j < HIDDEN; j++) {
                sum += h[j] * w[j];
            }
            logits[i] = sum;
            if (sum > max) max = sum;
        }
        float total = 0f;
        float[] probs = new float[10];
        for (int i = 0; i < 10; i++) {
            probs[i] = (float) Math.exp(logits[i] - max);
            total += probs[i];
        }
        for (int i = 0; i < 10; i++) {
            probs[i] /= total;
        }
        return probs;
    }

    public static final class Result {
        public final int digit;
        public final float confidence;

        Result(int digit, float confidence) {
            this.digit = digit;
            this.confidence = confidence;
        }
    }
}
