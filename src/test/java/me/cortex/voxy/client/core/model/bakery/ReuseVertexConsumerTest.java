package me.cortex.voxy.client.core.model.bakery;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.lwjgl.system.MemoryUtil;

public final class ReuseVertexConsumerTest {
    private static void equal(int expected, int actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        var consumer = new ReuseVertexConsumer();
        try {
            int[] vertices = new int[32];
            for (int i = 0; i < 4; i++) {
                vertices[i * 8] = Float.floatToRawIntBits(i * 0.125f);
                vertices[i * 8 + 1] = Float.floatToRawIntBits(0.75f - i * 0.0625f);
                vertices[i * 8 + 2] = Float.floatToRawIntBits(i * 0.03125f);
                vertices[i * 8 + 4] = Float.floatToRawIntBits(i * 0.0625f);
                vertices[i * 8 + 5] = Float.floatToRawIntBits(0.875f - i * 0.03125f);
            }
            var quad = new BakedQuad(vertices, 0, Direction.UP, null, true);
            consumer.quad(quad, false, true);
            equal(1, consumer.quadCount());
            for (int i = 0; i < 4; i++) {
                long address = consumer.getAddress() + i * ReuseVertexConsumer.VERTEX_FORMAT_SIZE;
                for (int axis = 0; axis < 3; axis++) equal(vertices[i * 8 + axis], MemoryUtil.memGetInt(address + axis * 4));
                equal(5, MemoryUtil.memGetInt(address + 12)); // discard and tint, not cube geometry
                equal(vertices[i * 8 + 4], MemoryUtil.memGetInt(address + 16));
                equal(vertices[i * 8 + 5], MemoryUtil.memGetInt(address + 20));
            }
            consumer.reset().quad(quad, true, true);
            equal(4, MemoryUtil.memGetInt(consumer.getAddress() + 12)); // leaves force-solid retains tint
            consumer.reset().setDefaultMeta(5).vertex(0.125, 0.25, 0.5).uv(0.75f, 0.875f).endVertex();
            equal(Float.floatToRawIntBits(0.125f), MemoryUtil.memGetInt(consumer.getAddress()));
            equal(5, MemoryUtil.memGetInt(consumer.getAddress() + 12));
            equal(Float.floatToRawIntBits(0.75f), MemoryUtil.memGetInt(consumer.getAddress() + 16));
            System.out.println("ReuseVertexConsumer: non-cube packed position/UV, tint/discard and fluid-chain transport passed (no GL)");
        } finally {
            consumer.free();
        }
    }
}
