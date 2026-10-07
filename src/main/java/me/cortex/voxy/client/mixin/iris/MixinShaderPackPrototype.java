package me.cortex.voxy.client.mixin.iris;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.iris.DhPrototype;
import me.cortex.voxy.client.iris.DhShaderEnvironment;
import me.cortex.voxy.client.iris.IGetDhShaderEnvironment;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.ShaderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.nio.file.Path;
import java.util.Map;

@Mixin(value = ShaderPack.class, remap = false)
public class MixinShaderPackPrototype implements IGetDhShaderEnvironment {
    @Unique private DhShaderEnvironment voxy$dhEnvironment;

    @Inject(method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;)V", at = @At("RETURN"))
    private void voxy$captureEnvironment(Path root, Map<String, String> options, ImmutableList<StringPair> environment, CallbackInfo ci) {
        this.voxy$dhEnvironment = DhPrototype.loadedEnvironment();
    }

    @Override
    public DhShaderEnvironment voxy$getDhShaderEnvironment() { return this.voxy$dhEnvironment; }

    @ModifyVariable(method = "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;)V", at = @At("HEAD"), argsOnly = true)
    private static ImmutableList<StringPair> voxy$prototypeEnvironment(ImmutableList<StringPair> environment, Path root, Map<String, String> options, ImmutableList<StringPair> ignored) {
        return DhPrototype.environment(root, options, environment);
    }
}
