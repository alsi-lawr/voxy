package me.cortex.voxy.commonImpl.mixin.chunky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.popcraft.chunky.platform.FabricWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.CompletableFuture;

@Mixin(value = FabricWorld.class, remap = false)
public class MixinFabricWorld {
    @WrapOperation(method = "getChunkAtAsync", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkHolder;getOrScheduleFuture(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ChunkMap;)Ljava/util/concurrent/CompletableFuture;", remap = true))
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> voxy$captureChunk(
            ChunkHolder holder, ChunkStatus status, ChunkMap map,
            Operation<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> original) {
        return original.call(holder, status, map).thenApply(result -> {
            result.left().ifPresent(chunk -> {
                if (chunk instanceof LevelChunk levelChunk) VoxelIngestService.tryAutoIngestChunk(levelChunk);
            });
            return result;
        });
    }
}
