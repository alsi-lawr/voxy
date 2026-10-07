package me.cortex.voxy.client.mixin.minecraft.session;

import me.cortex.voxy.client.ClientSessionEvents;
import me.cortex.voxy.client.core.util.PerformanceCapture;
import me.cortex.voxy.client.core.util.GPUTiming;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void voxy$beginPerformanceFrame(boolean renderLevel, CallbackInfo ci) {
        if (!PerformanceCapture.ENABLED) return;
        PerformanceCapture.beginFrame(((Minecraft) (Object) this).level != null);
        GPUTiming.INSTANCE.beginClientFrame();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void voxy$endPerformanceFrame(boolean renderLevel, CallbackInfo ci) {
        if (!PerformanceCapture.ENABLED) return;
        GPUTiming.INSTANCE.endClientFrame();
        var client = (Minecraft) (Object) this;
        PerformanceCapture.endFrame(renderLevel && client.level != null && client.screen == null
                && client.getOverlay() == null && client.isWindowActive() && !client.isPaused(),
                client.level != null, client.isPaused(), client.isWindowActive());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void voxy$finishPerformanceCapture(CallbackInfo ci) {
        if (!PerformanceCapture.ENABLED) return;
        GPUTiming.INSTANCE.finishCapture();
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
    private void voxy$injectWorldClose(CallbackInfo ci) {
        if (ClientSessionEvents.inSession) {
            ClientSessionEvents.sessionEnd();
        }
    }
}
