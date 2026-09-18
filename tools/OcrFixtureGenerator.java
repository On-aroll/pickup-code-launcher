import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regenerate the checked-in grayscale glyph fixture with JDK 17 (no Android runtime). */
public class OcrFixtureGenerator {
    public static void main(String[] args) throws Exception {
        int w = 32, h = 80;
        Path output = Path.of("app/src/test/resources/ocr_glyphs.bin");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(output))) {
            out.writeInt(w);
            out.writeInt(h);
            for (int digit = 0; digit < 10; digit++) {
                BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 52));
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.drawString(Integer.toString(digit), 0, 65);
                g.dispose();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) out.writeByte(image.getRGB(x, y) & 255);
                }
            }
        }
        System.out.println(output);
    }
}
