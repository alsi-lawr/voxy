package me.cortex.voxy.client.core.model.bakery;

import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import link.infra.indium.renderer.mesh.EncodingFormat;
import link.infra.indium.renderer.render.AbstractBlockRenderContext;
import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

/** Indium's vanilla/mesh defaults require provider-native context and emitter types. */
final class IndiumModelContext extends AbstractBlockRenderContext {
    private final FabricModelBakery bakery;
    private final Consumer<Mesh> meshConsumer = mesh -> mesh.outputTo(getEmitter());
    private final MutableQuadViewImpl emitter = new MutableQuadViewImpl() {
        { this.data = new int[EncodingFormat.TOTAL_STRIDE]; clear(); }
        @Override public void emitDirectly() { bakery.emitQuad(this); }
    };

    IndiumModelContext(FabricModelBakery bakery) {
        this.bakery = bakery;
    }

    // Keep the optional superclass out of the generic bakery's verifier frame.
    static RenderContext create(FabricModelBakery bakery) { return new IndiumModelContext(bakery); }

    @Override public QuadEmitter getEmitter() { emitter.clear(); return emitter; }
    @Override public QuadEmitter getVanillaModelEmitter() { return getEmitter(); }
    @Override public boolean isFaceCulled(Direction face) { return false; }
    @Override public BakedModelConsumer bakedModelConsumer() { return bakery.bakedModelConsumer(); }
    @Override public boolean hasTransform() { return bakery.hasTransform(); }
    @Override public void pushTransform(QuadTransform transform) { bakery.pushTransform(transform); }
    @Override public void popTransform() { bakery.popTransform(); }
    @Override public Consumer<Mesh> meshConsumer() { return meshConsumer; }

    @Override protected LightDataAccess getLightCache() {
        throw new IllegalStateException("Voxy model baking does not use Indium terrain lighting");
    }

    @Override protected void bufferQuad(MutableQuadViewImpl quad, Material material) {
        throw new IllegalStateException("Voxy model baking does not use Indium terrain buffers");
    }
}
