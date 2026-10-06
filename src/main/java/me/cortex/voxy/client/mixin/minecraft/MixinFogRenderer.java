package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogRenderer.class, priority = 900)
public class MixinFogRenderer {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void voxy$modifyFog(Camera camera, FogRenderer.FogMode fogMode,
            float renderDistance, boolean thickFog, float partialTick, CallbackInfo ci) {
        if (fogMode != FogRenderer.FogMode.FOG_TERRAIN) return;
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderFogColor();
        // 1.20 combines the far-plane and environmental ranges. Do not mistake the
        // ordinary air far-plane range for environmental fog in the Voxy pass.
        boolean environmental = thickFog || camera.getFluidInCamera() != FogType.NONE ||
                camera.getEntity() instanceof LivingEntity entity &&
                        (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS));
        FogParameters.current = new FogParameters(environmental ? start : 99999999,
                environmental ? end : 99999999, color[0], color[1], color[2], color[3]);
        if (!VoxyConfig.CONFIG.isRenderingEnabled() || IVoxyRenderSystemHolder.getNullable() == null) return;
        if (end >= 10 && (VoxyConfig.CONFIG.getFogMode().removesVanillaEnvFog || !environmental)) {
            RenderSystem.setShaderFogStart(99999999);
            RenderSystem.setShaderFogEnd(99999999);
        }
    }
}
