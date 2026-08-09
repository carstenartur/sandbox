import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Verifies that a Help screenshot differs only in transient GTK overlay-scrollbar pixels. */
public final class VerifyHelpScreenshotDiff {
    private static final int MAX_CHANGED_PIXELS = 6_000;
    private static final int MAX_CHANNEL_DELTA = 40;

    private VerifyHelpScreenshotDiff() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: VerifyHelpScreenshotDiff <baseline.png> <generated.png>");
        }
        Path baselinePath = Path.of(args[0]);
        Path generatedPath = Path.of(args[1]);
        BufferedImage baseline = requireImage(baselinePath);
        BufferedImage generated = requireImage(generatedPath);
        if (baseline.getWidth() != generated.getWidth() || baseline.getHeight() != generated.getHeight()) {
            fail("Image dimensions changed from " + baseline.getWidth() + "x" + baseline.getHeight()
                    + " to " + generated.getWidth() + "x" + generated.getHeight());
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
                maximumDelta = Math.max(maximumDelta, maximumChannelDelta(expected, actual));
                if (!isTransientGtkRegion(x, y)) {
                    outsideTransientRegions++;
                    if (firstUnexpectedX < 0) {
                        firstUnexpectedX = x;
                        firstUnexpectedY = y;
                    }
                }
            }
        }

        if (outsideTransientRegions > 0) {
            fail(outsideTransientRegions + " changed pixels lie outside the two GTK scrollbar regions; first at "
                    + firstUnexpectedX + "," + firstUnexpectedY);
        }
        if (changed > MAX_CHANGED_PIXELS) {
            fail("Too many changed pixels for GTK scrollbar animation: " + changed + " > " + MAX_CHANGED_PIXELS);
        }
        if (maximumDelta > MAX_CHANNEL_DELTA) {
            fail("A color channel changed by " + maximumDelta + ", exceeding " + MAX_CHANNEL_DELTA);
        }
        System.out.println("Accepted " + changed
                + " transient GTK overlay-scrollbar pixels; maximum channel delta " + maximumDelta + '.');
    }

    private static BufferedImage requireImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Not a readable PNG image: " + path);
        }
        return image;
    }

    private static boolean isTransientGtkRegion(int x, int y) {
        boolean verticalScrollbar = x >= 625 && x <= 646 && y >= 118 && y <= 345;
        boolean horizontalScrollbarAndCheckbox = x >= 15 && x <= 625 && y >= 670 && y <= 710;
        return verticalScrollbar || horizontalScrollbarAndCheckbox;
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
