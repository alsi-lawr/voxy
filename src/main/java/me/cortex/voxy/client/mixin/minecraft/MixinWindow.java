package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Window.class)
public class MixinWindow {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", remap = false), index = 1)
    private int voxy$requestOpenGL46(int hint, int value) {
        return switch (hint) {
            case GLFW.GLFW_CONTEXT_VERSION_MAJOR -> 4;
            case GLFW.GLFW_CONTEXT_VERSION_MINOR -> 6;
            default -> value;
        };
    }
}
