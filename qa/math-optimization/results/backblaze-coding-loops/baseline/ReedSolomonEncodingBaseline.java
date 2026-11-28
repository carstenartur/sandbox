import com.backblaze.erasure.CodingLoop;
import com.backblaze.erasure.ReedSolomon;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class ReedSolomonEncodingBaseline {
    private static final int BUFFER_SETS = 4;

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--variants")) {
            for (CodingLoop loop : CodingLoop.ALL_CODING_LOOPS) {
                System.out.println(loop.getClass().getSimpleName());
            }
            return;
        }
        if (args.length != 5) {
            throw new IllegalArgumentException("Expected variant, data shards, parity shards, bytes, offset");
        }
        CodingLoop selected = Arrays.stream(CodingLoop.ALL_CODING_LOOPS)
                .filter(loop -> loop.getClass().getSimpleName().equals(args[0]))
                .findFirst().orElseThrow();
        int data = Integer.parseInt(args[1]);
        int parity = Integer.parseInt(args[2]);
        int count = Integer.parseInt(args[3]);
        int offset = Integer.parseInt(args[4]);

        ReedSolomon codec = new ReedSolomon(data, parity, selected);
        ReedSolomon reference = ReedSolomon.create(data, parity);
        byte[][][] buffers = new byte[BUFFER_SETS][data + parity][offset + count + 11];
        byte[][][] expected = new byte[BUFFER_SETS][data + parity][];
        Random input = new Random(381691);
        for (int set = 0; set < BUFFER_SETS; set++) {
            for (int index = 0; index < data + parity; index++) {
                input.nextBytes(buffers[set][index]);
                expected[set][index] = buffers[set][index].clone();
            }
            reference.encodeParity(expected[set], offset, count);
            codec.encodeParity(buffers[set], offset, count);
            check(buffers[set], expected[set], reference, offset, count);
        }
        var allocations = ManagementFactory.getPlatformMXBean(com.sun.management.ThreadMXBean.class);
        boolean track = allocations != null && allocations.isThreadAllocatedMemorySupported();
        if (track && !allocations.isThreadAllocatedMemoryEnabled()) {
            allocations.setThreadAllocatedMemoryEnabled(true);
        }
        int cursor = 0;
        for (int window = 0; window <= 3; window++) {
            long duration = TimeUnit.MILLISECONDS.toNanos(window == 0 ? 400 : 250);
            long before = track ? allocations.getCurrentThreadAllocatedBytes() : -1;
            long start = System.nanoTime();
            long elapsed;
            long operations = 0;
            do {
                codec.encodeParity(buffers[cursor], offset, count);
                cursor = (cursor + 1) % BUFFER_SETS;
                operations++;
                elapsed = System.nanoTime() - start;
            } while (elapsed < duration);
            long bytes = track ? allocations.getCurrentThreadAllocatedBytes() - before : -1;
            if (window > 0) {
                System.out.println(window + "," + operations + "," + elapsed + "," + bytes);
            }
        }
        for (int set = 0; set < BUFFER_SETS; set++) {
            check(buffers[set], expected[set], reference, offset, count);
        }
    }

    private static void check(byte[][] actual, byte[][] expected, ReedSolomon reference, int offset, int count) {
        if (!Arrays.deepEquals(actual, expected) || !reference.isParityCorrect(actual, offset, count)) {
            throw new AssertionError("Mismatch in data, parity or guard bytes");
        }
    }
}
