package me.cortex.voxy.client;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.ChatFormatting;

import java.util.List;

public final class DebugEntries {
    private DebugEntries() {}

    public static void append(List<String> lines) {
        ChatFormatting status = !VoxyCommon.isAvailable() ? ChatFormatting.RED
            : VoxyCommon.getInstance() == null ? ChatFormatting.YELLOW
            : IVoxyRenderSystemHolder.getNullable() == null ? ChatFormatting.DARK_GREEN : ChatFormatting.GREEN;
        lines.add(status + "voxy-" + VoxyCommon.MOD_VERSION);
        VoxyDebugScreenEntry.display(lines);
    }
}
