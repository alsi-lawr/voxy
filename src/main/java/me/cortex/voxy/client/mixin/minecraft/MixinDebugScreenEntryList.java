package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.DebugEntries;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenEntryList {
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void voxy$appendDebug(CallbackInfoReturnable<List<String>> cir) {
        var lines = new ArrayList<>(cir.getReturnValue());
        DebugEntries.append(lines);
        cir.setReturnValue(lines);
    }
}
