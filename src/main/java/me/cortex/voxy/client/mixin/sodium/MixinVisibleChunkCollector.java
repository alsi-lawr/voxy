package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.SectionPos;

@Mixin(value = VisibleChunkCollector.class, remap = false)
public class MixinVisibleChunkCollector {
    @Inject(method = "visit", at = @At("HEAD"))
    private void voxy$gatherVisibleSection(RenderSection section, boolean visible, CallbackInfo ci) {
        if (!visible || !section.isBuilt() || IrisUtil.irisShadowActive()) return;
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer != null && renderer.visbleSectionStream != null) {
            renderer.visbleSectionStream.put(SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ()));
        }
    }
}
