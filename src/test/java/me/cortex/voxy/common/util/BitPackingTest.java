package me.cortex.voxy.common.util;

import java.util.Random;

/** Headless checks against an independent bit-position oracle; no native/game setup. */
public final class BitPackingTest {
    private static long compress(long value, long mask) {
        long result = 0, output = 1;
        while (mask != 0) {
            long bit = Long.lowestOneBit(mask);
            if ((value & bit) != 0) result |= output;
            mask ^= bit;
            output <<= 1;
        }
        return result;
    }

    private static long expand(long value, long mask) {
        long result = 0, input = 1;
        while (mask != 0) {
            long bit = Long.lowestOneBit(mask);
            if ((value & input) != 0) result |= bit;
            mask ^= bit;
            input <<= 1;
        }
        return result;
    }

    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }

    private static void check(long value) {
        equal(expand(value, 0x1249), BitPacking.spreadMorton5(value));
        equal(expand(value, 0x4210842108421L), BitPacking.spreadEveryFive11(value));
        int v = (int) value;
        equal(compress(Integer.toUnsignedLong(v), 0x1249), BitPacking.compactMorton5(v));
        equal(compress(Integer.toUnsignedLong(v), 0xCCC), BitPacking.upperPairs4(v));
        equal(compress(Integer.toUnsignedLong(v), 0x333), BitPacking.lowerPairs4(v));
        equal(compress(Integer.toUnsignedLong(v), 0x6318), BitPacking.upperPairs5(v));
        equal(compress(Integer.toUnsignedLong(v), 0x1CE7), BitPacking.lowerTriples5(v));
    }

    public static void main(String[] args) {
        for (int i = 0; i < 32768; i++) {
            check(i);
            int x = i & 31, y = i >>> 5 & 31, z = i >>> 10;
            int morton = (int) (BitPacking.spreadMorton5(x) |
                    BitPacking.spreadMorton5(y) << 1 | BitPacking.spreadMorton5(z) << 2);
            equal(x, BitPacking.compactMorton5(morton));
            equal(y, BitPacking.compactMorton5(morton >>> 1));
            equal(z, BitPacking.compactMorton5(morton >>> 2));
        }
        var random = new Random(4326987911L);
        for (int i = 0; i < 100000; i++) check(random.nextLong());
        System.out.println("BitPacking: all 32768 section positions and 100000 seeded full-width inputs passed");
    }
}
