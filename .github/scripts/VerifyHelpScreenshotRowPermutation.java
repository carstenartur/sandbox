import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Accepts either a pixel-for-pixel reproducible Help screenshot or the exact
 * two-row permutation produced when the stock LTK preview returns the same two
 * selected files in the opposite presentation order.
 *
 * <p>No pixel is ignored: acceptance after permutation requires the complete
 * images to become identical after swapping the two full-width tree rows.</p>
 */
public final class VerifyHelpScreenshotRowPermutation {
    private static final int FIRST_ROW_Y = 109;
    private static final int ROW_HEIGHT = 23;
    private static final int ROW_COUNT = 2;

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

        if (imagesEqual(baseline, candidate)) {
            System.out.println("Screenshot is pixel-reproducible: " + candidatePath);
            return;
        }
        if (equalsAfterTwoRowPermutation(baseline, candidate)) {
            System.out.println("Accepted exact LTK two-file row permutation: " + candidatePath);
            return;
        }

        throw new IllegalStateException(describeDifference(baselinePath, baseline,
                candidatePath, candidate));
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

    private static boolean imagesEqual(BufferedImage baseline, BufferedImage candidate) {
        for (int y = 0; y < baseline.getHeight(); y++) {
            for (int x = 0; x < baseline.getWidth(); x++) {
                if (baseline.getRGB(x, y) != candidate.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean equalsAfterTwoRowPermutation(BufferedImage baseline,
            BufferedImage candidate) {
        int secondRowY = FIRST_ROW_Y + ROW_HEIGHT;
        int afterRowsY = secondRowY + ROW_HEIGHT;
        for (int y = 0; y < baseline.getHeight(); y++) {
            int candidateY = y;
            if (y >= FIRST_ROW_Y && y < secondRowY) {
                candidateY = y + ROW_HEIGHT;
            } else if (y >= secondRowY && y < afterRowsY) {
                candidateY = y - ROW_HEIGHT;
            }
            for (int x = 0; x < baseline.getWidth(); x++) {
                if (baseline.getRGB(x, y) != candidate.getRGB(x, candidateY)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String describeDifference(Path baselinePath, BufferedImage baseline,
            Path candidatePath, BufferedImage candidate) {
        long changed = 0;
        int minX = baseline.getWidth();
        int minY = baseline.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < baseline.getHeight(); y++) {
            for (int x = 0; x < baseline.getWidth(); x++) {
                if (baseline.getRGB(x, y) != candidate.getRGB(x, y)) {
                    changed++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return candidatePath + " is neither identical to " + baselinePath
                + " nor its exact two-row LTK permutation; changedPixels=" + changed
                + ", bounds=" + minX + "," + minY + "-" + (maxX + 1) + "," + (maxY + 1);
    }
}
