package me.cortex.voxy.client.mixin.minecraft.util;

import me.cortex.voxy.client.LoadException;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockableEventLoop.class)
public abstract class MixinBlockableEventLoop {
    @Redirect(method = "doRunTask", at = @At(value = "INVOKE",
        target = "Lorg/slf4j/Logger;error(Lorg/slf4j/Marker;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
    private void voxy$surfaceLoadFailure(Logger logger, Marker marker, String message, Object thread, Object error) {
        if (error instanceof LoadException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw exception;
        }
        logger.error(marker, message, thread, error);
    }
}
