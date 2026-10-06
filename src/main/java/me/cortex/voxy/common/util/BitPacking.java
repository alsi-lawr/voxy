package me.cortex.voxy.common.util;

public final class BitPacking {
    private BitPacking() {}

    public static long spreadMorton5(long value) {
        value &= 0x1FL;
        value = (value | value << 8) & 0x100FL;
        value = (value | value << 4) & 0x10C3L;
        value = (value | value << 2) & 0x1249L;
        return value;
    }

    public static long spreadEveryFive11(long value) {
        value &= 0x7FFL;
        value = (value | value << 32) & 0x700000000FFL;
        value = (value | value << 16) & 0x70000F0000FL;
        value = (value | value << 8) & 0x40300C0300C03L;
        value = (value | value << 4) & 0x4210842108421L;
        return value;
    }

    public static int compactMorton5(int value) {
        value &= 0x1249;
        value = (value | value >>> 2) & 0x10C3;
        value = (value | value >>> 4) & 0x100F;
        return (value | value >>> 8) & 0x1F;
    }

    public static int upperPairs4(int value) {
        return (value >>> 2 & 3) | (value >>> 4 & 12) | (value >>> 6 & 48);
    }

    public static int lowerPairs4(int value) {
        return (value & 3) | (value >>> 2 & 12) | (value >>> 4 & 48);
    }

    public static int upperPairs5(int value) {
        return (value >>> 3 & 3) | (value >>> 6 & 12) | (value >>> 9 & 48);
    }

    public static int lowerTriples5(int value) {
        return (value & 7) | (value >>> 2 & 56) | (value >>> 4 & 448);
    }
}
