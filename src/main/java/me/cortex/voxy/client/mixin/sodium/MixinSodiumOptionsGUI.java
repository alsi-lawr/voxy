package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.config.VoxyConfigMenu;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = SodiumOptionsGUI.class, remap = false)
public class MixinSodiumOptionsGUI implements VoxyConfigMenu.ConfigScreen {
    @Shadow @Final private List<OptionPage> pages;

    @Unique private OptionPage voxy$configPage;

    @Override
    public OptionPage voxy$getConfigPage() { return this.voxy$configPage; }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voxy$addPages(CallbackInfo ci) {
        if (VoxyCommon.isAvailable()) {
            var voxyPages = new VoxyConfigMenu().pages();
            this.voxy$configPage = voxyPages.get(0);
            pages.addAll(voxyPages);
        }
    }
}
