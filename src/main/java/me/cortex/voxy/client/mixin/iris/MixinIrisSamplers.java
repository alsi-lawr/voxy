package me.cortex.voxy.client.mixin.iris;

import com.google.common.collect.ImmutableSet;
import me.cortex.voxy.client.iris.VoxySamplers;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = IrisSamplers.class, remap = false)
public class MixinIrisSamplers {
    @org.spongepowered.asm.mixin.injection.ModifyArgs(method = "addRenderTargetSamplers", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/sampler/SamplerHolder;addDynamicSampler(Lnet/irisshaders/iris/gl/texture/TextureType;Ljava/util/function/IntSupplier;Lnet/irisshaders/iris/gl/sampler/GlSampler;[Ljava/lang/String;)Z"))
    private static void voxy$prototypeDhDepth(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,
            SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped, RenderTargets targets, boolean fullscreen,
            WorldRenderingPipeline pipeline) {
        if (pipeline instanceof IrisRenderingPipeline iris && me.cortex.voxy.client.iris.DhPrototype.activeEnvironment()) {
            String[] names = args.get(3);
            if (names[0].equals("dhDepthTex") || names[0].equals("dhDepthTex1")) {
                boolean opaque = names[0].equals("dhDepthTex1");
                args.set(1, (java.util.function.IntSupplier) () -> VoxySamplers.prototypeDepth(iris, opaque));
                args.set(2, ((me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData)iris).voxy$getDepthSampler());
            }
        }
    }

    @Inject(method = "addRenderTargetSamplers", at = @At("TAIL"))
    private static void voxy$injectSamplers(SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped, RenderTargets renderTargets, boolean isFullscreenPass, WorldRenderingPipeline pipeline, CallbackInfo ci) {
        if (pipeline instanceof IrisRenderingPipeline ipipe) {
            VoxySamplers.addSamplers(ipipe, samplers);
        }
    }
}
