package me.cortex.voxy.client.core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class PerformanceCaptureTest {
    public static void main(String[] args) throws Exception {
        Path output = Files.createTempDirectory("voxy-performance-capture-");
        try {
            var capture = new PerformanceCapture(output, 2);
            capture.begin(100);
            capture.pass(0, 7);
            capture.pass(0, 11);
            capture.pass(2, 13);
            capture.end(150, true);
            capture.begin(200);
            capture.end(240, false);
            capture.begin(300);
            capture.end(350, true);
            // Deferred GPU receipts can arrive after a later client frame.
            capture.gpu(1, 1, new long[]{400, 420, 450});
            capture.gpu(0, 2, new long[]{500, 530});
            for (int i = 0; i < 70; i++) capture.gpu(0, 0, new long[]{0, 1});
            capture.write();
            var frames = Files.readAllLines(output.resolve("frames.csv"));
            if (frames.size() != 3) throw new AssertionError("CPU capture exceeded capacity");
            long[] first = row(frames.get(1));
            long[] second = row(frames.get(2));
            if (!Arrays.equals(first, new long[]{0, 100, 50, -1, 1, 3, 18, 0, 13, 0, 0, 0, 0, 0})
                    || !Arrays.equals(second, new long[]{1, 200, 40, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})) {
                throw new AssertionError("Frame accounting or pass reset failed");
            }
            var gpu = Files.readAllLines(output.resolve("gpu.csv"));
            if (gpu.size() != 65 || !Arrays.equals(row(gpu.get(1)), new long[]{1, 1, 0, 20})
                    || !Arrays.equals(row(gpu.get(2)), new long[]{1, 1, 1, 30})
                    || !Arrays.equals(row(gpu.get(3)), new long[]{0, 2, 0, 30})) {
                throw new AssertionError("Deferred frame linkage or GPU capacity failed");
            }
            if (!Files.readString(output.resolve("capture.txt")).contains("droppedGpuIntervals=9\n")) {
                throw new AssertionError("GPU overflow was not disclosed");
            }
            byte[] frozen = Files.readAllBytes(output.resolve("frames.csv"));
            capture.write();
            var collision = new PerformanceCapture(output, 1);
            try {
                collision.write();
                throw new AssertionError("Existing capture was overwritten");
            } catch (IllegalStateException expected) {
                if (!(expected.getCause() instanceof java.nio.file.FileAlreadyExistsException)) throw expected;
            }
            if (!Arrays.equals(frozen, Files.readAllBytes(output.resolve("frames.csv")))) {
                throw new AssertionError("Prior capture was changed");
            }
            System.out.println("Performance capture bounded accounting, deferred linkage and preservation passed");
        } finally {
            try (var paths = Files.walk(output)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static long[] row(String csv) {
        return Arrays.stream(csv.split(",")).mapToLong(Long::parseLong).toArray();
    }
}
