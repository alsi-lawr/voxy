package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.client.iris.IGetVoxyPatchData;
import me.cortex.voxy.client.iris.IrisShaderPatch;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline implements IGetVoxyPatchData, IGetIrisVoxyPipelineData {
    @Shadow @Final private CustomUniforms customUniforms;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
    @Unique private net.irisshaders.iris.gl.sampler.GlSampler voxy$depthSampler;
    @Unique private me.cortex.voxy.client.iris.DhTranslationFailure voxy$loadFailure;
    @Unique IrisShaderPatch patchData;
    @Unique
    IrisVoxyRenderPipelineData pipeline;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/transform/ShaderPrinter;resetPrintState()V", shift = At.Shift.AFTER))
    private void voxy$injectPatchDataStore(ProgramSet programSet, CallbackInfo ci) {
        if (IrisUtil.SHADER_SUPPORT) {
            try { this.patchData = ((IGetVoxyPatchData) programSet).voxy$getPatchData(); }
            catch (me.cortex.voxy.client.iris.DhTranslationFailure failure) { this.voxy$loadFailure = failure; }
        }
    }

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;shadowTargetsSupplier:Ljava/util/function/Supplier;", opcode = 181, shift = At.Shift.AFTER))
    private void voxy$requireAuthoredShadowTargets(ProgramSet programSet, CallbackInfo ci) {
        if (patchData != null && patchData.prototype != null && patchData.prototype.shadowEnabled()) {
            // Use Iris' own lazy target owner before it assembles shadow clears and its renderer.
            var targets = ((IrisRenderingPipelineAccessor)(Object)this).getShadowTargetsSupplier().get();
            targets.getOrCreate(0);
            targets.getOrCreate(1);
        }
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;createSetupComputes([Lnet/irisshaders/iris/shaderpack/programs/ComputeSource;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;)[Lnet/irisshaders/iris/gl/program/ComputeProgram;"))
    private void voxy$injectPipeline(ProgramSet programSet, CallbackInfo ci) {
        if (this.patchData != null) {
            if (this.patchData.prototype == null) {
                this.pipeline = IrisVoxyRenderPipelineData.buildPipeline((IrisRenderingPipeline)(Object)this, this.patchData, this.customUniforms, this.shaderStorageBufferHolder);
            } else {
                try {
                    this.pipeline = IrisVoxyRenderPipelineData.buildPipeline((IrisRenderingPipeline)(Object)this, this.patchData, this.customUniforms, this.shaderStorageBufferHolder);
                    this.patchData.prototype.preparePrograms();
                } catch (RuntimeException failure) {
                    this.voxy$loadFailure = failure instanceof me.cortex.voxy.client.iris.DhTranslationFailure typed ? typed
                            : this.patchData.prototype.failure(me.cortex.voxy.client.iris.DhTranslationFailure.Code.RELOAD_FAILED, "adapter", failure);
                    this.patchData.prototype.close();
                    this.pipeline = null;
                }
            }
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voxy$rejectFailedCandidate(ProgramSet programSet, CallbackInfo ci) {
        if (this.voxy$loadFailure != null) {
            // Finish Iris initialization so its existing destroy path can release its own targets/renderers safely.
            var failure = this.voxy$loadFailure;
            ((IrisRenderingPipeline)(Object)this).destroy();
            throw failure;
        }
    }

    @Inject(method = "beginLevelRendering", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;activeTexture(I)V", ordinal = 0, shift = At.Shift.BEFORE, remap = false), remap = false)
    private void voxy$injectViewportSetup(CallbackInfo ci) {
        if (IrisUtil.CAPTURED_VIEWPORT_PARAMETERS != null) {
            var renderer = IVoxyRenderSystemHolder.getNullable();
            if (renderer != null) {
                var viewport = IrisUtil.CAPTURED_VIEWPORT_PARAMETERS.apply(renderer);
                if (viewport != null && this.pipeline != null && this.pipeline.prototype != null) {
                    this.pipeline.prototype.beginFrame(viewport);
                }
                IrisUtil.CAPTURED_VIEWPORT_PARAMETERS = null;
                IrisUtil.USED_IRIS_VIEWPORT = true;
            }
        }
    }

    @Override
    public net.irisshaders.iris.gl.sampler.GlSampler voxy$getDepthSampler() {
        if (voxy$depthSampler == null) voxy$depthSampler = new net.irisshaders.iris.gl.sampler.GlSampler(false, true, false, false);
        return voxy$depthSampler;
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void voxy$destroyDepthSampler(CallbackInfo ci) {
        if (this.pipeline != null && this.pipeline.thePipeline != null) {
            var holder = IVoxyRenderSystemHolder.getNullableHolder();
            if (holder != null) holder.voxy$shutdownRenderer();
        }
        if (patchData != null && patchData.prototype != null) patchData.prototype.close();
        this.pipeline = null;
        this.patchData = null;
        IrisUtil.CAPTURED_VIEWPORT_PARAMETERS = null;
        IrisUtil.USED_IRIS_VIEWPORT = false;
        if (voxy$depthSampler != null) {
            voxy$depthSampler.destroy();
            voxy$depthSampler = null;
        }
    }

    @Override
    public IrisShaderPatch voxy$getPatchData() {
        return this.patchData;
    }

    @Override
    public IrisVoxyRenderPipelineData voxy$getPipelineData() {
        return this.pipeline;
    }
}
