import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Accepts either a pixel-for-pixel reproducible Help screenshot or the exact
 * two-row permutation produced when the stock LTK preview returns the same two
 * selected files in the opposite presentation order.
 *
 * <p>The Ubuntu GTK renderer can also vary a few low-delta pixels on the left
 * edge of the disabled {@code Next} button. That exception is limited to the
 * observed 3-by-23-pixel edge; every difference outside it remains fatal.</p>
 */
public final class VerifyHelpScreenshotRowPermutation {
    private static final int FIRST_ROW_Y = 109;
    private static final int ROW_HEIGHT = 23;
    private static final int ROW_COUNT = 2;

    private static final int GTK_NEXT_EDGE_MIN_X = 927;
    private static final int GTK_NEXT_EDGE_MAX_X_EXCLUSIVE = 930;
    private static final int GTK_NEXT_EDGE_MIN_Y = 851;
    private static final int GTK_NEXT_EDGE_MAX_Y_EXCLUSIVE = 874;
    private static final long MAX_GTK_NEXT_EDGE_CHANGED_PIXELS = 42;
    private static final int MAX_GTK_NEXT_EDGE_CHANNEL_DELTA = 5;

    private VerifyHelpScreenshotRowPermutation() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: VerifyHelpScreenshotRowPermutation <baseline.png> <candidate.png>");
        }

        Path baselinePath = Path.of(args[0]);
        Path candidatePath = Path.of(args[1]);
        BufferedImage baseline = requireImage(baselinePath);
        BufferedImage candidate = requireImage(candidatePath);
        requireSameDimensions(baselinePath, baseline, candidatePath, candidate);

        Difference direct = compare(baseline, candidate, false);
        if (direct.changedPixels() == 0) {
            System.out.println("Screenshot is pixel-reproducible: " + candidatePath);
            return;
        }
        if (isGtkNextButtonEdgeVariation(direct)) {
            System.out.println("Accepted GTK disabled-Next-button edge variation: "
                    + candidatePath + "; " + direct.describe());
            return;
        }

        Difference permuted = compare(baseline, candidate, true);
        if (permuted.changedPixels() == 0) {
            System.out.println("Accepted exact LTK two-file row permutation: " + candidatePath);
            return;
        }
        if (isGtkNextButtonEdgeVariation(permuted)) {
            System.out.println(
                    "Accepted LTK two-file row permutation with GTK disabled-Next-button edge variation: "
                            + candidatePath + "; " + permuted.describe());
            return;
        }

        throw new IllegalStateException(candidatePath + " is neither identical to " + baselinePath
                + " nor its accepted LTK two-row presentation; direct=" + direct.describe()
                + ", permuted=" + permuted.describe());
    }

    private static BufferedImage requireImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Not a readable PNG image: " + path);
        }
        return image;
    }

    private static void requireSameDimensions(Path baselinePath, BufferedImage baseline,
            Path candidatePath, BufferedImage candidate) {
        if (baseline.getWidth() != candidate.getWidth()
                || baseline.getHeight() != candidate.getHeight()) {
            throw new IllegalStateException(candidatePath + " has dimensions "
                    + candidate.getWidth() + "x" + candidate.getHeight() + ", expected "
                    + baseline.getWidth() + "x" + baseline.getHeight() + " from " + baselinePath);
        }
        int requiredHeight = FIRST_ROW_Y + ROW_HEIGHT * ROW_COUNT;
        if (baseline.getHeight() < requiredHeight) {
            throw new IllegalStateException("Screenshot is too short for the verified LTK row region: "
                    + baseline.getHeight() + " < " + requiredHeight);
        }
    }

    private static Difference compare(BufferedImage baseline, BufferedImage candidate,
            boolean permuteRows) {
        long changed = 0;
        int minX = baseline.getWidth();
        int minY = baseline.getHeight();
        int maxX = -1;
        int maxY = -1;
        int maximumChannelDelta = 0;
        int secondRowY = FIRST_ROW_Y + ROW_HEIGHT;
        int afterRowsY = secondRowY + ROW_HEIGHT;

        for (int y = 0; y < baseline.getHeight(); y++) {
            int candidateY = y;
            if (permuteRows && y >= FIRST_ROW_Y && y < secondRowY) {
                candidateY = y + ROW_HEIGHT;
            } else if (permuteRows && y >= secondRowY && y < afterRowsY) {
                candidateY = y - ROW_HEIGHT;
            }
            for (int x = 0; x < baseline.getWidth(); x++) {
                int expected = baseline.getRGB(x, y);
                int actual = candidate.getRGB(x, candidateY);
                if (expected == actual) {
                    continue;
                }
                changed++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maximumChannelDelta = Math.max(maximumChannelDelta,
                        maximumChannelDelta(expected, actual));
            }
        }
        return new Difference(changed, minX, minY, maxX, maxY, maximumChannelDelta);
    }

    private static boolean isGtkNextButtonEdgeVariation(Difference difference) {
        return difference.changedPixels() > 0
                && difference.changedPixels() <= MAX_GTK_NEXT_EDGE_CHANGED_PIXELS
                && difference.minX() >= GTK_NEXT_EDGE_MIN_X
                && difference.maxX() < GTK_NEXT_EDGE_MAX_X_EXCLUSIVE
                && difference.minY() >= GTK_NEXT_EDGE_MIN_Y
                && difference.maxY() < GTK_NEXT_EDGE_MAX_Y_EXCLUSIVE
                && difference.maximumChannelDelta() <= MAX_GTK_NEXT_EDGE_CHANNEL_DELTA;
    }

    private static int maximumChannelDelta(int first, int second) {
        int alpha = Math.abs((first >>> 24) - (second >>> 24));
        int red = Math.abs(((first >>> 16) & 0xff) - ((second >>> 16) & 0xff));
        int green = Math.abs(((first >>> 8) & 0xff) - ((second >>> 8) & 0xff));
        int blue = Math.abs((first & 0xff) - (second & 0xff));
        return Math.max(Math.max(alpha, red), Math.max(green, blue));
    }

    private record Difference(long changedPixels, int minX, int minY, int maxX, int maxY,
            int maximumChannelDelta) {
        private String describe() {
            if (changedPixels == 0) {
                return "changedPixels=0";
            }
            return "changedPixels=" + changedPixels + ", bounds=" + minX + "," + minY + "-"
                    + (maxX + 1) + "," + (maxY + 1)
                    + ", maximumChannelDelta=" + maximumChannelDelta;
        }
    }
}
