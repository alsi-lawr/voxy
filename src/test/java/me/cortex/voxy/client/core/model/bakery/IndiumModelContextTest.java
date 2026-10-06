package me.cortex.voxy.client.core.model.bakery;

import link.infra.indium.mixin.renderer.MixinBakedModel;
import link.infra.indium.renderer.IndiumRenderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;

public final class IndiumModelContextTest {
    private static final class VanillaModel implements BakedModel, MixinBakedModel {
        private final BakedQuad quad;
        private int faceCalls;
        VanillaModel() {
            int[] data = new int[32];
            for (int i = 0; i < 4; i++) {
                data[i * 8] = Float.floatToRawIntBits(i * 0.125f);
                data[i * 8 + 1] = Float.floatToRawIntBits(0.75f);
                data[i * 8 + 2] = Float.floatToRawIntBits(0.25f);
                data[i * 8 + 3] = -1;
                data[i * 8 + 4] = Float.floatToRawIntBits(0.125f);
                data[i * 8 + 5] = Float.floatToRawIntBits(0.25f);
            }
            quad = new BakedQuad(data, 2, Direction.UP, null, false);
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
            faceCalls++;
            return face == Direction.UP || face == null ? List.of(quad) : List.of();
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RendererAccess.INSTANCE.registerRenderer(IndiumRenderer.INSTANCE);
        var renderer = RendererAccess.INSTANCE.getRenderer();
        var material = renderer.materialFinder().blendMode(BlendMode.TRANSLUCENT)
                .disableDiffuse(true).emissive(true).find();
        var opaque = new ReuseVertexConsumer();
        var translucent = new ReuseVertexConsumer(1);
        var model = new VanillaModel();
        var order = new ArrayList<Integer>();
        var bakery = new FabricModelBakery(opaque, translucent);
        var state = Blocks.STONE.defaultBlockState();
        try {
            boolean rejected = false;
            try {
                bakery.bake(new FabricBakedModel() {
                    @Override public void emitBlockQuads(net.minecraft.world.level.BlockAndTintGetter view,
                            BlockState state, BlockPos pos, java.util.function.Supplier<RandomSource> random,
                            RenderContext context) {
                        model.emitBlockQuads(view, state, pos, random, context);
                    }
                }, null, state);
            } catch (ClassCastException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Independent context did not reproduce provider cast");
            var wrongMeshBuilder = renderer.meshBuilder();
            wrongMeshBuilder.getEmitter().fromVanilla(model.quad, material, Direction.UP).emit();
            rejected = false;
            try { wrongMeshBuilder.build().outputTo(bakery.getEmitter()); }
            catch (ClassCastException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Independent emitter did not reproduce provider mesh cast");
            bakery.bake(new FabricBakedModel() {
                @Override public void emitBlockQuads(net.minecraft.world.level.BlockAndTintGetter view,
                        BlockState state, BlockPos pos, java.util.function.Supplier<RandomSource> random,
                        RenderContext context) {
                    var carrier = IndiumModelContext.create((FabricModelBakery) context);
                    carrier.pushTransform(quad -> {
                        order.add(1);
                        if (!quad.material().emissive()) throw new AssertionError("Emissive material lost in dispatch");
                        quad.pos(0, quad.x(0) + 0.125f, quad.y(0), quad.z(0));
                        quad.uv(0, 0.5f, 0.75f);
                        return true;
                    });
                    carrier.pushTransform(quad -> { order.add(2); quad.material(material); return true; });
                    model.emitBlockQuads(view, state, pos, random, carrier);
                    carrier.popTransform();
                    carrier.popTransform();
                    if (carrier.hasTransform()) throw new AssertionError("Transforms not popped");
                    var meshBuilder = renderer.meshBuilder();
                    meshBuilder.getEmitter().fromVanilla(model.quad, material, Direction.UP).emit();
                    carrier.pushTransform(quad -> { order.add(3); quad.uv(0, 0.875f, 0.5f); return true; });
                    carrier.meshConsumer().accept(meshBuilder.build());
                    carrier.popTransform();
                    carrier.bakedModelConsumer().accept(model, state);
                    carrier.pushTransform(quad -> false);
                    carrier.getEmitter().fromVanilla(model.quad, material, Direction.UP).emit();
                    carrier.popTransform();
                }
            }, null, state);
            if (model.faceCalls != 14 || !order.equals(List.of(2, 1, 2, 1, 3))) throw new AssertionError("All-face/transform order changed");
            if (opaque.quadCount() != 2 || translucent.quadCount() != 3) throw new AssertionError("Provider/fallback/mesh/filter transport changed");
            long first = translucent.getAddress();
            if (MemoryUtil.memGetFloat(first) != 0.125f || MemoryUtil.memGetFloat(first + 16) != 0.5f
                    || MemoryUtil.memGetFloat(first + 20) != 0.75f || MemoryUtil.memGetInt(first + 12) != 5)
                throw new AssertionError("Non-cube geometry, transformed UV or tint/discard lost");
            if (opaque.anyShaded || translucent.anyShaded) throw new AssertionError("Disable-diffuse material lost");
            long meshFirst = first + 8L * ReuseVertexConsumer.VERTEX_FORMAT_SIZE;
            if (MemoryUtil.memGetFloat(meshFirst + 16) != 0.875f || MemoryUtil.memGetFloat(meshFirst + 20) != 0.5f)
                throw new AssertionError("Provider mesh replay skipped transform");
            System.out.println("Exact Indium vanilla and mesh defaults reproduced both old casts; carrier preserved all-face non-cube, LIFO transform/material/emissive callback, mesh/fallback/filter transport (no GL)");
        } finally { opaque.free(); translucent.free(); }
    }
}
