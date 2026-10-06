package me.cortex.voxy.common.world;

import java.util.Arrays;
import java.util.Random;
import jdk.incubator.vector.LongVector;
import me.cortex.voxy.common.world.other.Mapper;

public final class NonAirCountTest {
    static final long BLOCK_MASK = 0xFFFFFL << 27;
    static int cases;
    static int blocks;
    static int oracle(long[] data) {
        int count = 0;
        for (long block : data) { if ((block & BLOCK_MASK) != 0) count++; }
        return count;
    }

    static void countAgreement(long[] data) {
        int expected = oracle(data);
        int original = 0;
        for (long block : data) original += Mapper.isNotAirInt(block);
        if (original != expected || NonAirCount.count(data) != expected) {
            throw new AssertionError("count mismatch");
        }
        cases++;
        blocks += data.length;
    }

    public static void main(String[] args) {
        Random random = new Random(4326987911L);
        long[] ids = new long[1 << 20];
        for (int i = 0; i < ids.length; i++) ids[i] = ((long) i << 27) | (random.nextLong() & ~BLOCK_MASK);
        countAgreement(ids);
        for (int base = 0; base < ids.length; base += 32768) {
            countAgreement(Arrays.copyOfRange(ids, base, base + 32768));
        }
        long[] lanes = new long[LongVector.SPECIES_PREFERRED.length()];
        for (int id = 0; id < 1 << 20; id++) {
            for (int i = 0; i < lanes.length; i++) lanes[i] = ((long) id << 27) | (random.nextLong() & ~BLOCK_MASK);
            countAgreement(lanes);
        }
        for (int length = 0; length < 2 * LongVector.SPECIES_PREFERRED.length() + 3; length++) {
            long[] tail = new long[length];
            for (int sample = 0; sample < 1000; sample++) {
                for (int i = 0; i < length; i++) {
                    tail[i] = random.nextLong();
                    if ((i + sample) % 4 == 0) tail[i] &= ~BLOCK_MASK;
                }
                countAgreement(tail);
            }
        }
        long[] fullwidth = new long[1_000_000];
        for (int i = 0; i < fullwidth.length; i++) {
            fullwidth[i] = random.nextLong();
            if (i % 4 == 0) fullwidth[i] &= ~BLOCK_MASK;
        }
        countAgreement(fullwidth);
        System.out.println("PASS non-air count cases=" + cases + " tested cells=" + blocks);
    }
}
