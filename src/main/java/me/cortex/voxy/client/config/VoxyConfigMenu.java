package me.cortex.voxy.client.config;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.ClientSessionEvents;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.NormalRenderPipeline;
import me.cortex.voxy.client.core.SSAO;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.control.*;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.*;

public final class VoxyConfigMenu implements OptionStorage<VoxyConfig> {
    public interface ConfigScreen {
        OptionPage voxy$getConfigPage();
    }

    private final VoxyConfig config = VoxyConfig.CONFIG;
    private final List<OptionPage> pages;
    private Option<Boolean> enabled;
    private Option<Boolean> rendering;
    private boolean updateThreads;
    private boolean reloadIris;
    private boolean applyRendering;
    private boolean disableInstance;

    public VoxyConfigMenu() {
        enabled = bool("enabled", () -> config.enabled, value -> {
            config.enabled = value;
            if (value && ClientSessionEvents.inSession) VoxyCommon.createInstance();
            disableInstance = !value;
            reloadIris = true;
        }, () -> true, OptionFlag.REQUIRES_RENDERER_RELOAD);
        rendering = bool("rendering", () -> config.enableRendering, value -> {
            config.enableRendering = value;
            applyRendering = true;
            reloadIris = true;
        }, () -> enabled.getValue());
        BooleanSupplier active = () -> enabled.getValue() && rendering.getValue();
        BooleanSupplier nativePipeline = () -> active.getAsBoolean() && !IrisUtil.irisShadersEnabledInConfig();

        pages = List.of(
            new OptionPage(Component.translatable("voxy.config.general"), ImmutableList.of(
                group(enabled),
                group(integer("serviceThreads", () -> config.serviceThreads, value -> {
                    config.serviceThreads = value; updateThreads = true;
                }, 1, CpuLayout.getCoreCount(), ControlValueFormatter.number(), () -> enabled.getValue(), null),
                bool("useSodiumBuilder", () -> !config.dontUseSodiumBuilderThreads, value -> {
                    config.dontUseSodiumBuilderThreads = !value; updateThreads = true;
                }, () -> enabled.getValue(), OptionFlag.REQUIRES_RENDERER_RELOAD)),
                group(bool("ingest", () -> config.ingestEnabled, value -> config.ingestEnabled = value,
                    () -> enabled.getValue())))),
            new OptionPage(Component.translatable("voxy.config.rendering"), ImmutableList.of(
                group(rendering),
                group(integer("subDivisionSize", () -> subDiv2ln(config.subDivisionSize),
                    value -> config.subDivisionSize = ln2subDiv(value), 0, SUBDIV_IN_MAX,
                    value -> Component.literal(Integer.toString(Math.round(ln2subDiv(value)))), active, OptionImpact.HIGH),
                integer("renderDistance", () -> Math.round(config.sectionRenderDistance * 16), value -> {
                    config.sectionRenderDistance = (float) value / 16;
                    var renderer = IVoxyRenderSystemHolder.getNullable();
                    if (renderer != null) renderer.setRenderDistance(config.sectionRenderDistance);
                }, 10, 64 * 16, value -> Component.literal(Integer.toString(value * 2)), active, OptionImpact.MEDIUM)),
                group(enumeration("environmental_fog", NormalRenderPipeline.FogMode.class,
                    config::getFogMode, config::setFogMode, nativePipeline, null),
                enumeration("ssao_mode", SSAO.SSAOMode.class, config::getSSAOMode,
                    config::setSSAOMode, nativePipeline, OptionImpact.MEDIUM)))));
    }

    public List<OptionPage> pages() { return pages; }
    @Override public VoxyConfig getData() { return config; }

    @Override public void save() {
        var holder = IVoxyRenderSystemHolder.getNullableHolder();
        if (disableInstance) {
            if (holder != null) holder.voxy$shutdownRenderer();
            VoxyCommon.shutdownInstance();
        } else if (applyRendering && holder != null) {
            if (config.enableRendering) {
                if (holder.voxy$getRenderSystem() == null) holder.voxy$createRenderer();
            } else holder.voxy$shutdownRenderer();
        }
        if (updateThreads) {
            var instance = VoxyCommon.getInstance();
            if (instance != null) instance.updateDedicatedThreads();
        }
        if (reloadIris) IrisUtil.reload();
        config.save();
        updateThreads = reloadIris = applyRendering = disableInstance = false;
    }

    private <T> OptionImpl.Builder<VoxyConfig, T> builder(Class<T> type, String key,
            Supplier<T> getter, Consumer<T> setter) {
        return OptionImpl.createBuilder(type, this)
            .setName(Component.translatable("voxy.config.general." + key))
            .setTooltip(Component.translatable("voxy.config.general." + key + ".tooltip"))
            .setBinding((config, value) -> setter.accept(value), config -> getter.get());
    }

    private Option<Boolean> bool(String key, Supplier<Boolean> getter, Consumer<Boolean> setter,
            BooleanSupplier active, OptionFlag... flags) {
        return SodiumConfigBuilder.option(builder(Boolean.class, key, getter, setter).setFlags(flags),
            TickBoxControl::new, active);
    }

    private Option<Integer> integer(String key, IntSupplier getter, IntConsumer setter, int min,
            int max, ControlValueFormatter formatter, BooleanSupplier active, OptionImpact impact) {
        return SodiumConfigBuilder.option(builder(Integer.class, key, getter::getAsInt, setter::accept)
            .setImpact(impact), option -> new SliderControl(option, min, max, 1, formatter), active);
    }

    private <T extends Enum<T>> Option<T> enumeration(String key, Class<T> type, Supplier<T> getter,
            Consumer<T> setter, BooleanSupplier active, OptionImpact impact) {
        Component[] names = java.util.Arrays.stream(type.getEnumConstants())
            .map(value -> Component.translatable("voxy.config.general." + key + "." + value.name().toLowerCase(Locale.ROOT)))
            .toArray(Component[]::new);
        return SodiumConfigBuilder.option(builder(type, key, getter, setter).setImpact(impact)
            .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD), option -> new CyclingControl<>(option, type, names), active);
    }

    private static OptionGroup group(Option<?>... options) {
        var group = OptionGroup.createBuilder();
        for (var option : options) group.add(option);
        return group.build();
    }

    private static final int SUBDIV_IN_MAX = 100;
    private static final double SUBDIV_MIN = 28;
    private static final double SUBDIV_MAX = 256;
    private static final double SUBDIV_CONST = Math.log(SUBDIV_MAX / SUBDIV_MIN) / Math.log(2);
    private static float ln2subDiv(int value) {
        return (float) (SUBDIV_MIN * Math.pow(2, SUBDIV_CONST * ((double) value / SUBDIV_IN_MAX)));
    }
    private static int subDiv2ln(float value) {
        return (int) ((Math.log(value / SUBDIV_MIN) / Math.log(2)) / SUBDIV_CONST * SUBDIV_IN_MAX);
    }
}
