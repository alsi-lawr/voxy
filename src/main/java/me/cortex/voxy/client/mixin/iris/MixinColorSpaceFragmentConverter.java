package me.cortex.voxy.client.mixin.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.voxy.client.iris.IColorSpaceConverterCleanup;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pathways.colorspace.ColorSpaceFragmentConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ColorSpaceFragmentConverter.class, remap = false)
public class MixinColorSpaceFragmentConverter implements IColorSpaceConverterCleanup {
    @Shadow private Program program;
    @Shadow private GlFramebuffer framebuffer;
    @Shadow private int swapTexture;

    @Override
    @Unique
    public void voxy$destroyConverter() {
        var ownedProgram = this.program;
        var ownedFramebuffer = this.framebuffer;
        int ownedTexture = this.swapTexture;
        this.program = null;
        this.framebuffer = null;
        this.swapTexture = 0;
        if (ownedProgram != null) ownedProgram.destroy();
        if (ownedFramebuffer != null) ownedFramebuffer.destroy();
        if (ownedTexture != 0) GlStateManager._deleteTexture(ownedTexture);
    }
}
