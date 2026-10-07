package me.cortex.voxy.client.core.util;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class PerformanceCapture {
    public static final boolean ENABLED = System.getProperty("voxy.performance.output") != null;
    private static final PerformanceCapture INSTANCE = ENABLED ? new PerformanceCapture(
            Path.of(System.getProperty("voxy.performance.output")), Integer.getInteger("voxy.performance.frames", 16384)) : null;
    private static final int FRAME_WIDTH = 14;
    private static final int GPU_WIDTH = 4;
    private final Path output;
    private final long[] frames;
    private final long[] gpu;
    private final long[] passTimes = new long[4];
    private long frameId = -1;
    private long start;
    private long cpuStart;
    private final ThreadMXBean threadTimes = ManagementFactory.getThreadMXBean();
    private long previousStart;
    private int frameCount;
    private int gpuCount;
    private int passCount;
    private int droppedGpu;
    private int droppedClientGpuFrames;
    private int droppedVoxyGpuPasses;
    private boolean closed;
    private boolean inFrame;
    private int pendingClientGpuFrames;
    private int pendingVoxyGpuPasses;

    PerformanceCapture(Path output, int capacity) {
        if (capacity < 1 || capacity > 65536) throw new IllegalArgumentException("Capture frames must be 1..65536");
        this.output = output;
        if (threadTimes.isCurrentThreadCpuTimeSupported() && !threadTimes.isThreadCpuTimeEnabled()) {
            threadTimes.setThreadCpuTimeEnabled(true);
        }
        this.frames = new long[capacity * FRAME_WIDTH];
        this.gpu = new long[capacity * 32 * GPU_WIDTH];
    }

    public static long currentFrame() { return ENABLED ? INSTANCE.frameId : -1; }
    public static boolean recording() { return ENABLED && INSTANCE.inFrame && INSTANCE.frameId < INSTANCE.frames.length / FRAME_WIDTH; }
    public static void beginFrame(boolean inWorld) {
        if (ENABLED) {
            INSTANCE.inFrame = inWorld;
            if (!inWorld) {
                INSTANCE.previousStart = 0;
                return;
            }
            INSTANCE.begin(System.nanoTime());
            INSTANCE.cpuStart = INSTANCE.threadTimes.isCurrentThreadCpuTimeSupported() ? INSTANCE.threadTimes.getCurrentThreadCpuTime() : -1;
        }
    }
    public static void endFrame(boolean eligible, boolean worldLoaded, boolean paused, boolean windowActive) {
        if (ENABLED && INSTANCE.inFrame) {
            long cpuEnd = INSTANCE.threadTimes.isCurrentThreadCpuTimeSupported() ? INSTANCE.threadTimes.getCurrentThreadCpuTime() : -1;
            INSTANCE.end(System.nanoTime(), eligible);
            INSTANCE.inFrame = false;
            if (INSTANCE.frameId < INSTANCE.frames.length / FRAME_WIDTH) {
                int offset = (INSTANCE.frameCount - 1) * FRAME_WIDTH;
                INSTANCE.frames[offset + 10] = INSTANCE.cpuStart < 0 || cpuEnd < 0 ? -1 : cpuEnd - INSTANCE.cpuStart;
                INSTANCE.frames[offset + 11] = worldLoaded ? 1 : 0;
                INSTANCE.frames[offset + 12] = paused ? 1 : 0;
                INSTANCE.frames[offset + 13] = windowActive ? 1 : 0;
            }
        }
    }
    public static void voxyPass(int pass, long nanos) { if (ENABLED && INSTANCE.inFrame) INSTANCE.pass(pass, nanos); }
    public static void gpuIntervals(long frame, int pass, long[] timestamps) {
        if (ENABLED) INSTANCE.gpu(frame, pass, timestamps);
    }
    public static void skipClientGpuFrame() { if (ENABLED) INSTANCE.droppedClientGpuFrames++; }
    public static void skipVoxyGpuPass() { if (ENABLED) INSTANCE.droppedVoxyGpuPasses++; }
    public static void finish(int pendingClientFrames, int pendingVoxyPasses) {
        if (ENABLED) {
            INSTANCE.pendingClientGpuFrames = pendingClientFrames;
            INSTANCE.pendingVoxyGpuPasses = pendingVoxyPasses;
            INSTANCE.write();
        }
    }

    void begin(long now) {
        frameId++;
        start = now;
        passCount = 0;
        java.util.Arrays.fill(passTimes, 0);
    }

    void pass(int pass, long nanos) {
        passTimes[pass] += nanos;
        passCount++;
    }

    void end(long now, boolean eligible) {
        if (frameCount * FRAME_WIDTH < frames.length) {
            int offset = frameCount++ * FRAME_WIDTH;
            frames[offset] = frameId;
            frames[offset + 1] = start;
            frames[offset + 2] = now - start;
            frames[offset + 3] = previousStart == 0 ? -1 : start - previousStart;
            frames[offset + 4] = eligible ? 1 : 0;
            frames[offset + 5] = passCount;
            System.arraycopy(passTimes, 0, frames, offset + 6, 4);
        }
        previousStart = start;
    }

    void gpu(long frame, int pass, long[] timestamps) {
        for (int i = 1; i < timestamps.length; i++) {
            if (gpuCount * GPU_WIDTH == gpu.length) {
                droppedGpu++;
                continue;
            }
            int offset = gpuCount++ * GPU_WIDTH;
            gpu[offset] = frame;
            gpu[offset + 1] = pass;
            gpu[offset + 2] = i - 1;
            gpu[offset + 3] = timestamps[i] - timestamps[i - 1];
        }
    }

    void write() {
        if (closed) return;
        closed = true;
        try {
            Files.createDirectories(output);
            writeRows(output.resolve("frames.csv"), "frame,startNanos,runTickNanos,startIntervalNanos,eligible,voxyPassCount,opaqueNanos,waterNanos,shadowOpaqueNanos,shadowWaterNanos,renderThreadCpuNanos,worldLoaded,paused,windowActive", frames, frameCount, FRAME_WIDTH);
            writeRows(output.resolve("gpu.csv"), "frame,pass,interval,gpuDeltaNanos", gpu, gpuCount, GPU_WIDTH);
            Files.writeString(output.resolve("capture.txt"), "frames=" + frameCount + "\nframeCapacity=" + frames.length / FRAME_WIDTH
                    + "\ngpuIntervals=" + gpuCount + "\ngpuCapacity=" + gpu.length / GPU_WIDTH + "\ndroppedGpuIntervals=" + droppedGpu
                    + "\ndroppedClientGpuFrames=" + droppedClientGpuFrames
                    + "\ndroppedVoxyGpuPasses=" + droppedVoxyGpuPasses
                    + "\npendingClientGpuFrames=" + pendingClientGpuFrames + "\npendingVoxyGpuPasses=" + pendingVoxyGpuPasses
                    + "\nlastObservedFrame=" + frameId + "\nprimitiveBufferBytes=" + (8L * (frames.length + gpu.length + passTimes.length))
                    + "\npasses=0:opaque,1:water,2:shadowOpaque,3:shadowWater,4:clientRunTick\n"
                    + "gpuScope=deferred timestamp intervals; pass4 brackets runTick submissions including idle bubbles, not GPU busy time; pending queries are not waited for\n",
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Voxy opt-in performance capture export failed", e);
        }
    }

    private static void writeRows(Path path, String header, long[] data, int count, int width) throws IOException {
        try (var writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW)) {
            writer.write(header);
            writer.newLine();
            for (int row = 0; row < count; row++) {
                for (int column = 0; column < width; column++) {
                    if (column != 0) writer.write(',');
                    writer.write(Long.toString(data[row * width + column]));
                }
                writer.newLine();
            }
        }
    }
}
