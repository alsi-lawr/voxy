package me.cortex.voxy.client.mixin.iris;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.FogParameters;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.glViewport;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "renderLevel", at = @At("HEAD"), order = 100)
    private void voxy$captureViewport(PoseStack poseStack, float partialTick, long finishTimeNano,
            boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
        if (IrisUtil.irisShaderPackEnabled() && IVoxyRenderSystemHolder.getNullableHolder() != null) {
            var target = Minecraft.getInstance().getMainRenderTarget();
            glViewport(0, 0, target.width, target.height);
            var pos = camera.getPosition();
            IrisUtil.CAPTURED_VIEWPORT_PARAMETERS = new IrisUtil.CapturedViewportParameters(
                new ChunkRenderMatrices(projection, poseStack.last().pose()), FogParameters.current,
                target.width, target.height, pos.x, pos.y, pos.z);
        }
    }
    @Inject(method = "renderChunkLayer", at = @At("HEAD"))
    private void voxy$renderDhStage(net.minecraft.client.renderer.RenderType layer, PoseStack poseStack,
            double x, double y, double z, Matrix4f projection, CallbackInfo ci) {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null || renderer.getDhPrototype() == null) return;
        boolean water = layer == net.minecraft.client.renderer.RenderType.translucent();
        if (!water && layer != net.minecraft.client.renderer.RenderType.solid()) return;
        var target = Minecraft.getInstance().getMainRenderTarget();
        Viewport<?> viewport;
        if (IrisUtil.irisShadowActive()) return;
        viewport = renderer.getViewport();
        if (!water && !IrisUtil.USED_IRIS_VIEWPORT) {
            viewport = renderer.setupViewport(projection, poseStack.last().pose(), FogParameters.current,
                    target.width, target.height, x, y, z);
            if (viewport != null) renderer.getDhPrototype().beginFrame(viewport);
        }
        if (!water) IrisUtil.USED_IRIS_VIEWPORT = false;
        var pipeline = (net.irisshaders.iris.pipeline.IrisRenderingPipeline)net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
        var previousPhase = pipeline.getPhase();
        net.irisshaders.iris.gl.program.ProgramUniforms.clearActiveUniforms();
        pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.fromTerrainRenderType(layer));
        net.irisshaders.iris.layer.GbufferPrograms.runPhaseChangeNotifier();
        try {
            if (water) renderer.renderDhWater(viewport, target.getDepthTextureId(), target.getColorTextureId());
            else renderer.renderOpaque(viewport, target.getDepthTextureId(), target.getColorTextureId());
        } finally {
            net.irisshaders.iris.gl.program.ProgramUniforms.clearActiveUniforms();
            pipeline.setPhase(previousPhase);
            net.irisshaders.iris.layer.GbufferPrograms.runPhaseChangeNotifier();
        }
    }

}
