package me.cortex.voxy.client.iris;

import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public final class DhPrototypeInputs {
    private DhPrototypeInputs() {}

    public static int faceColour(ColourDepthTextureData texture) {
        long r = 0, g = 0, b = 0, alpha = 0;
        int count = 0;
        for (int i = 0; i < texture.colour().length; i++) {
            if ((texture.depth()[i] & 255) == 0) continue;
            int pixel = texture.colour()[i];
            r += pixel & 255;
            g += (pixel >>> 8) & 255;
            b += (pixel >>> 16) & 255;
            alpha += pixel >>> 24;
            count++;
        }
        if (count == 0) return 0;
        return (int)(r / count) | ((int)(g / count) << 8) | ((int)(b / count) << 16) | ((int)(alpha / count) << 24);
    }

    // DH 3.3.3 BlockStateWrapper's ordered classifier, not Iris's ordinary block IDs.
    public static int material(BlockState state) {
        if (state.isAir()) return 14;
        String name = (BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + state.getValues().entrySet().stream()
                .map(entry -> entry.getKey().getName() + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining(" "))).toLowerCase(Locale.ROOT);
        if (state.is(BlockTags.LEAVES) || name.contains("bamboo") || name.contains("cactus") || name.contains("chorus_flower") || name.contains("mushroom")) return 1;
        if (state.is(Blocks.LAVA)) return 6;
        if (!state.getFluidState().isEmpty() || state.is(Blocks.WATER)) return 12;
        var sound = state.getSoundType();
        if (sound == SoundType.WOOD || name.contains("root") || sound == SoundType.CHERRY_WOOD) return 3;
        if (sound == SoundType.METAL || sound == SoundType.COPPER) return 4;
        if (name.contains("grass_block") || name.contains("grass_slab")) return 13;
        if (name.contains("dirt") || name.contains("gravel") || name.contains("mud") || name.contains("podzol") || name.contains("mycelium")) return 5;
        if (sound == SoundType.DEEPSLATE || sound == SoundType.DEEPSLATE_BRICKS || sound == SoundType.DEEPSLATE_TILES || sound == SoundType.POLISHED_DEEPSLATE || name.contains("deepslate")) return 7;
        if (state.is(BlockTags.BASE_STONE_NETHER)) return 11;
        if (name.contains("snow")) return 8;
        if (name.contains("sand")) return 9;
        if (name.contains("terracotta")) return 10;
        if (name.contains("stone") || name.contains("ore")) return 2;
        if (state.getLightEmission() > 0) return 15;
        return 0;
    }
}
