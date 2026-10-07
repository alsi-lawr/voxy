package me.cortex.voxy.client.mixin.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisRenderingPipelineAccessor {
    @Accessor
    net.irisshaders.iris.shadows.ShadowRenderTargets getShadowRenderTargets();

    @Accessor
    java.util.function.Supplier<net.irisshaders.iris.shadows.ShadowRenderTargets> getShadowTargetsSupplier();

    @Accessor
    RenderTargets getRenderTargets();
}
