package me.cortex.voxy.client;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.commonImpl.VoxyCommon;
import java.util.List;

public final class VoxyDebugScreenEntry {
    private VoxyDebugScreenEntry() {}

    public static void display(List<String> lines) {
        if (!VoxyCommon.isAvailable()) return;
        var instance = VoxyCommon.getInstance();
        if (instance == null) return;
        instance.addDebug(lines);
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer != null) renderer.addDebugInfo(lines);
    }
}
