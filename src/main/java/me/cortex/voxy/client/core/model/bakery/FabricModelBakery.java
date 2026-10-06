package me.cortex.voxy.client.core.model.bakery;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 1.20 Fabric model emission into the existing per-state software bakery, not a renderer. */
final class FabricModelBakery implements RenderContext {
    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    private final ReuseVertexConsumer opaque, translucent;
    private final MeshBuilder builder;
    private final RenderMaterial shaded, unshaded;
    private final RenderContext modelContext;
    private final Emitter emitter = new Emitter();
    private final ArrayList<QuadTransform> transforms = new ArrayList<>();
    private final RandomSource random = new SingleThreadedRandomSource(42L);
    private final Supplier<RandomSource> randomSupplier = () -> { random.setSeed(42L); return random; };
    private final Consumer<Mesh> meshConsumer = mesh -> mesh.outputTo(getEmitter());
    private QuadEmitter delegate;
    private BlockState state;
    private final BakedModelConsumer fallback = new BakedModelConsumer() {
        @Override public void accept(BakedModel model) { accept(model, state); }
        @Override public void accept(BakedModel model, BlockState modelState) {
            for (var face : FACES) {
                random.setSeed(42L);
                for (var quad : model.getQuads(modelState, face, random)) {
                    getEmitter().fromVanilla(quad, quad.isShade() ? shaded : unshaded, face).emit();
                }
            }
        }
    };

    FabricModelBakery(ReuseVertexConsumer opaque, ReuseVertexConsumer translucent) {
        this.opaque = opaque;
        this.translucent = translucent;
        var renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) throw new IllegalStateException(
                "A Fabric renderer provider (for example compatible Indium with Sodium 0.5) is required to bake custom Fabric models");
        this.builder = renderer.meshBuilder();
        this.shaded = renderer.materialFinder().find();
        this.unshaded = renderer.materialFinder().disableDiffuse(true).find();
        this.modelContext = FabricLoader.getInstance().isModLoaded("indium")
                ? IndiumModelContext.create(this) : this;
    }

    void bake(FabricBakedModel model, BlockAndTintGetter view, BlockState state) {
        this.state = state;
        try {
            model.emitBlockQuads(view, state, BlockPos.ZERO, randomSupplier, modelContext);
        } finally {
            if (modelContext == this) builder.build();
            else modelContext.getEmitter();
            transforms.clear();
            this.state = null;
        }
    }

    @Override public QuadEmitter getEmitter() {
        if (modelContext != this) return modelContext.getEmitter();
        delegate = builder.getEmitter();
        return emitter;
    }
    @Override public boolean hasTransform() { return !transforms.isEmpty(); }
    @Override public void pushTransform(QuadTransform transform) { transforms.add(transform); }
    @Override public void popTransform() { transforms.remove(transforms.size() - 1); }
    @Override public Consumer<Mesh> meshConsumer() { return meshConsumer; }
    @Override public BakedModelConsumer bakedModelConsumer() { return fallback; }

    void emitQuad(MutableQuadView quad) {
        boolean visible = true;
        for (int i = transforms.size() - 1; i >= 0 && visible; i--) visible = transforms.get(i).transform(quad);
        if (visible) {
            var layer = quad.material().blendMode().blockRenderLayer;
            if (layer == null) layer = ItemBlockRenderTypes.getChunkRenderType(state);
            var consumer = layer == RenderType.translucent() ? translucent : opaque;
            consumer.quad(quad, state.is(BlockTags.LEAVES), layer);
        }
    }

    private final class Emitter implements QuadEmitter {
        @Override public QuadEmitter emit() {
            emitQuad(delegate);
            delegate.emit();
            return this;
        }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter pos(int p0, float p1, float p2, float p3) { delegate.pos(p0, p1, p2, p3); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter color(int p0, int p1) { delegate.color(p0, p1); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter uv(int p0, float p1, float p2) { delegate.uv(p0, p1, p2); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter spriteBake(net.minecraft.client.renderer.texture.TextureAtlasSprite p0, int p1) { delegate.spriteBake(p0, p1); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter lightmap(int p0, int p1) { delegate.lightmap(p0, p1); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter normal(int p0, float p1, float p2, float p3) { delegate.normal(p0, p1, p2, p3); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter cullFace(net.minecraft.core.Direction p0) { delegate.cullFace(p0); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter nominalFace(net.minecraft.core.Direction p0) { delegate.nominalFace(p0); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter material(net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial p0) { delegate.material(p0); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter colorIndex(int p0) { delegate.colorIndex(p0); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter tag(int p0) { delegate.tag(p0); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter copyFrom(net.fabricmc.fabric.api.renderer.v1.mesh.QuadView p0) { delegate.copyFrom(p0); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter fromVanilla(int[] p0, int p1) { delegate.fromVanilla(p0, p1); return this; }
        @Override public net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter fromVanilla(net.minecraft.client.renderer.block.model.BakedQuad p0, net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial p1, net.minecraft.core.Direction p2) { delegate.fromVanilla(p0, p1, p2); return this; }
        @Override public float x(int p0) { return delegate.x(p0); }
        @Override public float y(int p0) { return delegate.y(p0); }
        @Override public float z(int p0) { return delegate.z(p0); }
        @Override public float posByIndex(int p0, int p1) { return delegate.posByIndex(p0, p1); }
        @Override public org.joml.Vector3f copyPos(int p0, org.joml.Vector3f p1) { return delegate.copyPos(p0, p1); }
        @Override public int color(int p0) { return delegate.color(p0); }
        @Override public float u(int p0) { return delegate.u(p0); }
        @Override public float v(int p0) { return delegate.v(p0); }
        @Override public org.joml.Vector2f copyUv(int p0, org.joml.Vector2f p1) { return delegate.copyUv(p0, p1); }
        @Override public int lightmap(int p0) { return delegate.lightmap(p0); }
        @Override public boolean hasNormal(int p0) { return delegate.hasNormal(p0); }
        @Override public float normalX(int p0) { return delegate.normalX(p0); }
        @Override public float normalY(int p0) { return delegate.normalY(p0); }
        @Override public float normalZ(int p0) { return delegate.normalZ(p0); }
        @Override public org.joml.Vector3f copyNormal(int p0, org.joml.Vector3f p1) { return delegate.copyNormal(p0, p1); }
        @Override public net.minecraft.core.Direction cullFace() { return delegate.cullFace(); }
        @Override public net.minecraft.core.Direction lightFace() { return delegate.lightFace(); }
        @Override public net.minecraft.core.Direction nominalFace() { return delegate.nominalFace(); }
        @Override public org.joml.Vector3f faceNormal() { return delegate.faceNormal(); }
        @Override public net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial material() { return delegate.material(); }
        @Override public int colorIndex() { return delegate.colorIndex(); }
        @Override public int tag() { return delegate.tag(); }
        @Override public void toVanilla(int[] p0, int p1) { delegate.toVanilla(p0, p1); }
    }
}
