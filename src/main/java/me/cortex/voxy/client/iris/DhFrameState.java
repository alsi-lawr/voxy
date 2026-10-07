package me.cortex.voxy.client.iris;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class DhFrameState {
    public final Matrix4f projection = new Matrix4f();
    public final Matrix4f inverseProjection = new Matrix4f();
    public final Matrix4f previousProjection = new Matrix4f();
    public boolean historyReset;
    public String resetReason = "first_frame";
    private int frame = -1;
    private int width;
    private int height;
    private double x, y, z;

    public void reset() {
        frame = -1;
        resetReason = "renderer_owner";
    }

    public void update(int frame, int width, int height, double x, double y, double z, Matrix4fc projection) {
        if (this.frame == frame) return;
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        resetReason = this.frame < 0 ? resetReason : !this.projection.isFinite() ? "projection_unready"
                : frame != this.frame + 1 ? "frame_gap"
                : width != this.width || height != this.height ? "resize"
                : dx * dx + dy * dy + dz * dz > 64 * 64 ? "camera_discontinuity" : "none";
        boolean reset = !resetReason.equals("none");
        historyReset = reset;
        previousProjection.set(reset ? projection : this.projection);
        this.projection.set(projection);
        inverseProjection.set(projection).invert();
        this.frame = frame;
        this.width = width;
        this.height = height;
        this.x = x; this.y = y; this.z = z;
    }
}
