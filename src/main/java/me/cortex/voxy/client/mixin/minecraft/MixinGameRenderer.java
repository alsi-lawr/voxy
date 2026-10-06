package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.core.VoxyRenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"))
    private void voxy$captureRawProjection(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        VoxyRenderSystem.RAW_GAME_PROJECTION.set(cir.getReturnValue());
    }
}
