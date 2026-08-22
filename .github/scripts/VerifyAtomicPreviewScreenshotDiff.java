import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Verifies atomic Cleanup preview screenshots while tolerating only the tiny
 * transient GTK button-chrome repaint that can remain after modal-shell
 * activation under Xvfb.
 */
public final class VerifyAtomicPreviewScreenshotDiff {
    private static final int MAX_CHANGED_PIXELS = 2_000;
    private static final int MAX_CHANNEL_DELTA = 12;

    private VerifyAtomicPreviewScreenshotDiff() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: VerifyAtomicPreviewScreenshotDiff <baseline.png> <generated.png>");
        }

        Path baselinePath = Path.of(args[0]);
        Path generatedPath = Path.of(args[1]);
        BufferedImage baseline = requireImage(baselinePath);
        BufferedImage generated = requireImage(generatedPath);
        if (baseline.getWidth() != generated.getWidth()
                || baseline.getHeight() != generated.getHeight()) {
            fail("Image dimensions changed from " + baseline.getWidth() + "x"
                    + baseline.getHeight() + " to " + generated.getWidth() + "x"
                    + generated.getHeight());
        }

        int changed = 0;
        int outsideTransientRegions = 0;
        int maximumDelta = 0;
        int firstUnexpectedX = -1;
        int firstUnexpectedY = -1;
        for (int y = 0; y < baseline.getHeight(); y++) {
            for (int x = 0; x < baseline.getWidth(); x++) {
                int expected = baseline.getRGB(x, y);
                int actual = generated.getRGB(x, y);
                if (expected == actual) {
                    continue;
                }

                changed++;
                maximumDelta = Math.max(maximumDelta,
                        maximumChannelDelta(expected, actual));
                if (!isTransientGtkButtonChrome(x, y, baseline.getWidth(), baseline.getHeight())) {
                    outsideTransientRegions++;
                    if (firstUnexpectedX < 0) {
                        firstUnexpectedX = x;
                        firstUnexpectedY = y;
                    }
                }
            }
        }

        if (outsideTransientRegions > 0) {
            fail(outsideTransientRegions
                    + " changed pixels lie outside the narrow GTK wizard-button chrome; first at "
                    + firstUnexpectedX + "," + firstUnexpectedY);
        }
        if (changed > MAX_CHANGED_PIXELS) {
            fail("Too many changed pixels for transient GTK button repainting: "
                    + changed + " > " + MAX_CHANGED_PIXELS);
        }
        if (maximumDelta > MAX_CHANNEL_DELTA) {
            fail("A color channel changed by " + maximumDelta
                    + ", exceeding the GTK repaint allowance of " + MAX_CHANNEL_DELTA);
        }

        System.out.println("Accepted " + changed
                + " transient GTK wizard-button pixels; maximum channel delta "
                + maximumDelta + '.');
    }

    private static BufferedImage requireImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Not a readable PNG image: " + path);
        }
        return image;
    }

    private static boolean isTransientGtkButtonChrome(int x, int y, int width, int height) {
        if (width != 1280 || height != 900) {
            return false;
        }

        int[][] buttons = {
                { 818, 927 },
                { 927, 1036 },
                { 1043, 1152 },
                { 1159, 1268 }
        };
        int chromeTop = 846;
        int chromeBottom = 890;
        for (int[] button : buttons) {
            int buttonLeft = button[0];
            int buttonRight = button[1];
            boolean inExpandedBounds = x >= buttonLeft - 5 && x <= buttonRight + 5
                    && y >= chromeTop && y <= chromeBottom;
            boolean onChrome = y <= 853 || y >= 884
                    || x <= buttonLeft + 4 || x >= buttonRight - 4;
            if (inExpandedBounds && onChrome) {
                return true;
            }
        }
        return false;
    }

    private static int maximumChannelDelta(int first, int second) {
        int alpha = Math.abs((first >>> 24) - (second >>> 24));
        int red = Math.abs(((first >>> 16) & 0xff) - ((second >>> 16) & 0xff));
        int green = Math.abs(((first >>> 8) & 0xff) - ((second >>> 8) & 0xff));
        int blue = Math.abs((first & 0xff) - (second & 0xff));
        return Math.max(Math.max(alpha, red), Math.max(green, blue));
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
