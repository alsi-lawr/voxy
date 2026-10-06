package me.cortex.voxy.client.core.rendering;

public record FogParameters(float environmentalStart, float environmentalEnd,
                            float red, float green, float blue, float alpha) {
    public static FogParameters current = new FogParameters(0, Float.MAX_VALUE, 0, 0, 0, 1);
}
