package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!VoxyCommon.isAvailable()) return null;
            var screen = SodiumOptionsGUI.createScreen(parent);
            if (screen instanceof SodiumOptionsGUI sodium) {
                sodium.setPage(((VoxyConfigMenu.ConfigScreen) sodium).voxy$getConfigPage());
            }
            return screen;
        };
    }
}
