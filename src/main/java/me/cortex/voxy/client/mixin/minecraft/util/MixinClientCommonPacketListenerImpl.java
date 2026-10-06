package me.cortex.voxy.client.mixin.minecraft.util;

import me.cortex.voxy.client.LoadException;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PacketUtils.class)
public class MixinClientCommonPacketListenerImpl {
    @Redirect(method = "method_11072", remap = false, at = @At(value = "INVOKE",
        target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
    private static void voxy$surfaceJoinFailure(Logger logger, String message, Object packet, Object error) {
        if (packet instanceof ClientboundLoginPacket && error instanceof Exception exception) {
            throw new LoadException("Force crashing due to exception during on game join", exception);
        }
        logger.error(message, packet, error);
    }
}
