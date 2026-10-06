package me.cortex.voxy.client.mixin.iris;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.iris.DhPrototype;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.ShaderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.nio.file.Path;
import java.util.Map;

@Mixin(value = ShaderPack.class, remap = false)
public class MixinShaderPackPrototype {
    @ModifyVariable(method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;)V", at = @At("HEAD"), argsOnly = true)
    private static ImmutableList<StringPair> voxy$prototypeEnvironment(ImmutableList<StringPair> environment, Path root, Map<String, String> options, ImmutableList<StringPair> ignored) {
        return DhPrototype.environment(root, options, environment);
    }
}
