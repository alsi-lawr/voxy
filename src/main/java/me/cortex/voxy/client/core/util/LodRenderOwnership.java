package me.cortex.voxy.client.core.util;

import me.cortex.voxy.common.Logger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public final class LodRenderOwnership {
    private static final boolean DH_INSTALLED = FabricLoader.getInstance().isModLoaded("distanthorizons");
    private static MethodHandle configs, graphics, renderingEnabled, getValue;
    private static boolean reported;

    public static boolean dhActive() {
        if (!DH_INSTALLED) return false;
        try {
            if (configs == null) {
                var lookup = MethodHandles.publicLookup();
                configs = lookup.unreflectGetter(Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs"));
                graphics = lookup.unreflect(Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("graphics"));
                renderingEnabled = lookup.unreflect(Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig").getMethod("renderingEnabled"));
                getValue = lookup.unreflect(Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod("getValue"));
            }
            Object config = configs.invoke();
            if (config == null) throw new IllegalStateException("DH delayed configuration is not ready");
            return (Boolean) getValue.invoke(renderingEnabled.invoke(graphics.invoke(config)));
        } catch (Throwable failure) {
            throw new IllegalStateException("VOXY_DH_LOD_OWNERSHIP_UNAVAILABLE: cannot establish the active DH renderer", failure);
        }
    }

    public static boolean suppressVoxyDraw() {
        boolean active = dhActive();
        if (active && !reported) {
            String message = "VOXY_DH_LOD_ENGINE_CONFLICT: Distant Horizons rendering is active; Voxy LOD draws are suppressed. Disable one LOD renderer.";
            Logger.error(message);
            Minecraft.getInstance().gui.getChat().addMessage(Component.literal(message));
            reported = true;
        } else if (!active) reported = false;
        return active;
    }
}
