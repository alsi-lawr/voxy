package me.cortex.voxy.common.world;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import me.cortex.voxy.common.world.other.Mapper;

final class NonAirCount {
    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    static int count(long[] data) {
        LongVector accumulator = LongVector.zero(SPECIES);
        int bound = SPECIES.loopBound(data.length);
        int i = 0;
        for (; i < bound; i += SPECIES.length()) {
            LongVector blocks = LongVector.fromArray(SPECIES, data, i);
            LongVector nonAir = blocks.lanewise(VectorOperators.LSHR, 27)
                    .and(0xFFFFFL).add(0xFFFFFL).lanewise(VectorOperators.LSHR, 20);
            accumulator = accumulator.add(nonAir);
        }
        int count = (int) accumulator.reduceLanes(VectorOperators.ADD);
        for (; i < data.length; i++) {
            count += Mapper.isNotAirInt(data[i]);
        }
        return count;
    }
}
