package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.iris.ShaderLoadError;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Iris.class, remap = false)
public class MixinIris {
    @org.spongepowered.asm.mixin.injection.Inject(method = "loadShaderpack", at = @At("HEAD"))
    private static void voxy$clearDisabledDhRequest(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var config = Iris.getIrisConfig();
        if (config != null && (!config.areShadersEnabled() || config.getShaderPackName().isEmpty())) {
            me.cortex.voxy.client.iris.DhPrototype.clearRequest();
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "destroyEverything", at = @At("RETURN"))
    private static void voxy$clearDhEnvironment(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        me.cortex.voxy.client.iris.DhPrototype.clearEnvironment();
    }

    @Redirect(method = "createPipeline", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/ShaderPack;getProgramSet(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;"))
    private static ProgramSet voxy$redirectProgramSet(ShaderPack shaderPack, NamespacedId dim) {
        try {
            return shaderPack.getProgramSet(dim);
        } catch (ShaderLoadError e) {
            Logger.error(e);
            return null;
        }
    }
}
