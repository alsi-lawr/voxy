package me.cortex.voxy.client.config;

import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class SodiumConfigBuilder {
    private SodiumConfigBuilder() {}

    static <T> Option<T> option(OptionImpl.Builder<VoxyConfig, T> builder,
                                Function<Option<T>, Control<T>> control, BooleanSupplier enabled) {
        var delegate = builder.setControl(option -> control.apply(option)).build();
        return new Option<T>() {
            private final Control<T> actualControl = control.apply(this);
            @Override public Component getName() { return delegate.getName(); }
            @Override public Component getTooltip() { return delegate.getTooltip(); }
            @Override public OptionImpact getImpact() { return delegate.getImpact(); }
            @Override public Control<T> getControl() { return actualControl; }
            @Override public T getValue() { return delegate.getValue(); }
            @Override public void setValue(T value) { delegate.setValue(value); }
            @Override public void reset() { delegate.reset(); }
            @Override public OptionStorage<?> getStorage() { return delegate.getStorage(); }
            @Override public boolean isAvailable() { return enabled.getAsBoolean(); }
            @Override public boolean hasChanged() { return delegate.hasChanged(); }
            @Override public void applyChanges() { delegate.applyChanges(); }
            @Override public Collection<OptionFlag> getFlags() { return delegate.getFlags(); }
        };
    }
}
