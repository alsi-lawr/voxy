package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.FogParameters;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShadowRenderer.class, remap = false)
public class MixinShadowRenderer {
    @Shadow @Final private boolean shouldRenderDH;

    @Inject(method = "renderShadows", at = @At(value = "FIELD",
            target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;shouldRenderTerrain:Z", opcode = 180))
    private void voxy$opaqueShadow(LevelRendererAccessor level, Camera camera, CallbackInfo ci) {
        voxy$renderShadow(camera, false);
    }

    @Inject(method = "renderShadows", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;copyPreTranslucentDepth(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;)V", shift = At.Shift.AFTER))
    private void voxy$translucentShadow(LevelRendererAccessor level, Camera camera, CallbackInfo ci) {
        voxy$renderShadow(camera, true);
    }

    @Unique
    private void voxy$renderShadow(Camera camera, boolean water) {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (!shouldRenderDH || renderer == null || renderer.getDhPrototype() == null || !renderer.getDhPrototype().shadowEnabled()) return;
        var shadows = ((IrisRenderingPipelineAccessor)Iris.getPipelineManager().getPipelineNullable()).getShadowRenderTargets();
        var pos = camera.getPosition();
        var viewport = water ? renderer.getViewport() : renderer.setupViewport(ShadowRenderer.PROJECTION,
                ShadowRenderer.MODELVIEW, FogParameters.current, shadows.getResolution(), shadows.getResolution(), pos.x, pos.y, pos.z);
        var target = Minecraft.getInstance().getMainRenderTarget();
        if (water) renderer.renderDhWater(viewport, target.getDepthTextureId(), target.getColorTextureId());
        else renderer.renderOpaque(viewport, target.getDepthTextureId(), target.getColorTextureId());
    }
}
