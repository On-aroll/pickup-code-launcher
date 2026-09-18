package cn.pickup.launcher;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises the production pixel pipeline, not just already-segmented model inputs. */
public class PickupOcrTest {
    private static TinyCnn model;
    private static byte[][] glyphs;
    private static int glyphW, glyphH;

    @BeforeClass
    public static void loadModel() throws Exception {
        try (FileInputStream in = new FileInputStream("src/main/assets/pickup_ocr.bin")) {
            model = TinyCnn.load(in);
        }
        try (DataInputStream in = new DataInputStream(PickupOcrTest.class.getResourceAsStream("/ocr_glyphs.bin"))) {
            glyphW = in.readInt();
            glyphH = in.readInt();
            glyphs = new byte[10][glyphW * glyphH];
            for (byte[] glyph : glyphs) in.readFully(glyph);
        }
    }

    private int[] screenshot(String digits, boolean dark, boolean antialias, int spacing) {
        int[] image = new int[1000 * 320];
        Arrays.fill(image, dark ? 0xFF000000 : 0xFFFFFFFF);
        // Offsets catch the old zero image-stride bug: the first rows are empty.
        int x = 70;
        for (char digit : digits.toCharArray()) {
            byte[] glyph = glyphs[digit - '0'];
            for (int yy = 0; yy < glyphH; yy++) {
                for (int xx = 0; xx < glyphW; xx++) {
                    int value = glyph[yy * glyphW + xx] & 255;
                    if (!antialias) value = value < 128 ? 0 : 255;
                    if (dark) value = 255 - value;
                    image[(140 + yy) * 1000 + x + xx] = 0xFF000000 | value * 0x010101;
                }
            }
            x += glyphW + spacing;
        }
        return image;
    }

    private PickupOcr.Result recognize(int[] image) {
        return PickupOcr.recognizePixels(image, 1000, 320, model);
    }

    @Test
    public void readsEntireFourToEightDigitCodesAtNonzeroOffset() {
        for (String code : new String[]{"4826", "01234", "836204", "1234567", "01234567"}) {
            PickupOcr.Result result = recognize(screenshot(code, false, true, 15));
            assertEquals(code, result.code);
            assertEquals(code.length(), result.confidences.length);
        }
    }

    @Test
    public void readsPureBlackAndWhiteWithoutErasingForeground() {
        assertEquals("836204", recognize(screenshot("836204", false, false, 15)).code);
    }

    @Test
    public void readsDarkBackground() {
        assertEquals("836204", recognize(screenshot("836204", true, false, 15)).code);
    }

    @Test
    public void neighboringDigitsAreNotMerged() {
        assertEquals("836204", recognize(screenshot("836204", false, true, 0)).code);
    }

    @Test
    public void transparentBackgroundIsCompositedOnWhite() {
        int[] image = screenshot("836204", false, false, 15);
        for (int i = 0; i < image.length; i++) {
            if (image[i] == 0xFFFFFFFF) image[i] = 0;
        }
        assertEquals("836204", recognize(image).code);
    }

    @Test
    public void choosesCompleteCodeBesideAnUnrelatedLongNumber() {
        int[] image = screenshot("13800138000", false, true, 15);
        int[] code = screenshot("836204", false, true, 15);
        System.arraycopy(code, 140 * 1000, image, 20 * 1000, 80 * 1000);
        assertEquals("836204", recognize(image).code);
    }

    @Test
    public void excessiveComponentsAreRejectedBeforeExpensiveCandidateSearch() {
        int[] pixels = new int[1000 * 1000];
        Arrays.fill(pixels, 0xFFFFFFFF);
        for (int top = 10; top < 980; top += 25) {
            for (int left = 10; left < 980; left += 25) {
                for (int y = top; y < top + 18; y++) {
                    Arrays.fill(pixels, y * 1000 + left, y * 1000 + left + 10, 0xFF000000);
                }
            }
        }
        assertEquals("", PickupOcr.recognizePixels(pixels, 1000, 1000, model).code);
    }

    @Test
    public void doesNotTruncateLongPhoneOrOrderNumbers() {
        for (String code : new String[]{"123456789", "13800138000", "1234567890123456"}) {
            assertEquals("", recognize(screenshot(code, false, true, 15)).code);
        }
    }

    @Test
    public void emptyImagesAndShortNumbersHaveNoCandidate() {
        for (int color : new int[]{0xFF000000, 0xFFFFFFFF, 0xFF888888}) {
            int[] pixels = new int[160 * 80];
            Arrays.fill(pixels, color);
            assertEquals("", PickupOcr.recognizePixels(pixels, 160, 80, model).code);
        }
        assertEquals("", recognize(screenshot("123", false, true, 15)).code);
    }

    @Test
    public void cancellationStopsPipeline() {
        Thread.currentThread().interrupt();
        try {
            PickupOcr.recognizePixels(new int[100], 10, 10, model);
            fail("Expected cancellation");
        } catch (CancellationException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
