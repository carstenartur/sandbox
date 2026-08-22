import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Verifies that committed JFace Cleanup execution-preview screenshots are real,
 * distinct captures rather than copies of the cleanup-profile configuration tab.
 *
 * <p>The SWTBot scenario is the semantic authority: before each capture it checks
 * the active LTK preview tree, the selected file, the concrete source diff,
 * selection behaviour, Apply and byte-exact Undo. This verifier binds those
 * semantic checkpoints to distinct PNG evidence and prevents a later workflow
 * step from silently replacing all captures with one stale image.</p>
 */
public final class VerifyRealCleanupPreviewScreenshots {
    private static final long MIN_PREVIEW_PAIR_DIFFERENCE = 1_000;
    private static final long MIN_CONFIGURATION_DIFFERENCE = 50_000;
    private static final int MIN_WIDTH = 1_000;
    private static final int MIN_HEIGHT = 700;

    private VerifyRealCleanupPreviewScreenshots() {
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: VerifyRealCleanupPreviewScreenshots "
                            + "<configuration.png> <single-file-preview.png> "
                            + "<selected-file-diff.png> <multi-file-selection.png>");
        }

        Screenshot configuration = load(Path.of(args[0]));
        List<Screenshot> previews = List.of(
                load(Path.of(args[1])),
                load(Path.of(args[2])),
                load(Path.of(args[3])));

        requireSameDimensions(configuration, previews);
        requireUniqueFiles(previews);

        for (Screenshot preview : previews) {
            long changed = changedPixels(configuration.image(), preview.image());
            if (changed < MIN_CONFIGURATION_DIFFERENCE) {
                fail(preview.path() + " differs from the configuration-tab screenshot in only "
                        + changed + " pixels; expected a real LTK execution preview");
            }
        }

        for (int first = 0; first < previews.size(); first++) {
            for (int second = first + 1; second < previews.size(); second++) {
                Screenshot left = previews.get(first);
                Screenshot right = previews.get(second);
                long changed = changedPixels(left.image(), right.image());
                if (changed < MIN_PREVIEW_PAIR_DIFFERENCE) {
                    fail(left.path() + " and " + right.path() + " differ in only " + changed
                            + " pixels; the documented preview states must be separate captures");
                }
            }
        }

        System.out.println("Verified three distinct real JFace Cleanup execution-preview screenshots:");
        for (Screenshot preview : previews) {
            System.out.println("- " + preview.path() + " sha256=" + preview.sha256());
        }
    }

    private static Screenshot load(Path path) throws IOException, NoSuchAlgorithmException {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) {
            throw new IOException("Screenshot is missing or empty: " + path);
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Not a readable PNG image: " + path);
        }
        if (image.getWidth() < MIN_WIDTH || image.getHeight() < MIN_HEIGHT) {
            fail(path + " is only " + image.getWidth() + "x" + image.getHeight()
                    + "; the full preview dialog is not visible");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String sha256 = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        return new Screenshot(path, image, sha256);
    }

    private static void requireSameDimensions(Screenshot configuration, List<Screenshot> previews) {
        for (Screenshot preview : previews) {
            if (configuration.image().getWidth() != preview.image().getWidth()
                    || configuration.image().getHeight() != preview.image().getHeight()) {
                fail(preview.path() + " has dimensions " + preview.image().getWidth() + "x"
                        + preview.image().getHeight() + ", expected "
                        + configuration.image().getWidth() + "x" + configuration.image().getHeight());
            }
        }
    }

    private static void requireUniqueFiles(List<Screenshot> previews) {
        Set<String> digests = new HashSet<>();
        List<Path> duplicates = new ArrayList<>();
        for (Screenshot preview : previews) {
            if (!digests.add(preview.sha256())) {
                duplicates.add(preview.path());
            }
        }
        if (!duplicates.isEmpty()) {
            fail("Real-preview screenshots are byte-identical: " + duplicates);
        }
    }

    private static long changedPixels(BufferedImage first, BufferedImage second) {
        long changed = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }

    private record Screenshot(Path path, BufferedImage image, String sha256) {
    }
}
