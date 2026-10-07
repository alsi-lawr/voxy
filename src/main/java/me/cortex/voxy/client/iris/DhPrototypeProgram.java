package me.cortex.voxy.client.iris;

import com.google.common.collect.ImmutableSet;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.rendering.Viewport;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

final class DhPrototypeProgram {
    private final int id;
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private final CustomUniforms customUniforms;
    private final ProgramSource source;
    private final BufferBlendOverride[] bufferBlends;
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix3f normalMatrix = new Matrix3f();
    private final float[] matrixValues = new float[16];
    private final float[] normalValues = new float[9];
    private final Map<String, Integer> locations = new LinkedHashMap<>();
    private final Map<String, Integer> samplerLocations = new LinkedHashMap<>();

    DhPrototypeProgram(int id, ProgramSource source, IrisRenderingPipeline pipeline, CustomUniforms customUniforms, boolean waterPass, boolean shadowPass) {
        this.id = id;
        this.source = source;
        this.customUniforms = customUniforms;
        var blends = new java.util.ArrayList<BufferBlendOverride>();
        for (var info : source.getDirectives().getBufferBlendOverrides()) {
            int[] buffers = source.getDirectives().getDrawBuffers();
            for (int i = 0; i < buffers.length; i++) if (buffers[i] == info.index()) blends.add(new BufferBlendOverride(i, info.blendMode()));
        }
        bufferBlends = blends.toArray(BufferBlendOverride[]::new);
        var uniformBuilder = ProgramUniforms.builder("voxy-003-" + source.getName(), id);
        for (var name : new String[]{"iris_ProjectionMatrix", "iris_ProjectionMatrixInverse", "iris_ModelViewMatrix", "iris_ModelViewMatrixInverse",
                "dhProjection", "dhProjectionInverse", "dhPreviousProjection"}) {
            uniformBuilder.externallyManagedUniform(name, UniformType.MAT4);
            locations.put(name, glGetUniformLocation(id, name));
        }
        uniformBuilder.externallyManagedUniform("iris_NormalMatrix", UniformType.MAT3);
        uniformBuilder.externallyManagedUniform("dhNearPlane", UniformType.FLOAT);
        uniformBuilder.externallyManagedUniform("dhFarPlane", UniformType.FLOAT);
        uniformBuilder.externallyManagedUniform("dhRenderDistance", UniformType.INT);
        for (var name : new String[]{"iris_NormalMatrix", "dhNearPlane", "dhFarPlane", "dhRenderDistance"}) locations.put(name, glGetUniformLocation(id, name));
        CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_VERTEX);
        BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniformBuilder);
        customUniforms.assignTo(uniformBuilder);
        customUniforms.mapholderToPass(uniformBuilder, this);
        uniforms = uniformBuilder.buildUniforms();
        var samplerBuilder = ProgramSamplers.builder(id, ImmutableSet.of(0, 1, 2));
        var imageBuilder = ProgramImages.builder(id);
        // Water writes fbTranslucent: its original arbitrary-pixel DH reads must use completed opaque depth instead.
        var passSamplers = VoxySamplers.waterDepthSamplers(samplerBuilder, () -> VoxySamplers.prototypeDepth(pipeline, waterPass));
        pipeline.addGbufferOrShadowSamplers(passSamplers, imageBuilder, shadowPass ? pipeline::getFlippedBeforeShadow : waterPass ? pipeline::getFlippedAfterTranslucent : pipeline::getFlippedAfterPrepare, shadowPass, false, true, false);
        samplers = samplerBuilder.build();
        images = imageBuilder.build();
        try (var stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1);
            var type = stack.mallocInt(1);
            int count = glGetProgrami(id, GL_ACTIVE_UNIFORMS);
            for (int i = 0; i < count; i++) {
                String name = glGetActiveUniform(id, i, size, type);
                if (type.get(0) == GL_SAMPLER_2D || type.get(0) == GL_SAMPLER_2D_SHADOW) {
                    for (int element = 0; element < size.get(0); element++) {
                        String entry = size.get(0) == 1 ? name : name.replace("[0]", "[" + element + "]");
                        samplerLocations.put(entry, glGetUniformLocation(id, entry));
                    }
                }
            }
        }
        DhPrototype.write(source.getName() + "-bindings.txt", "uniformLocations=" + locations + "\nreservedVoxySamplers=[0,1,2]\n");
    }

    String resourceBindings() {
        var receipt = new StringBuilder("program=" + id + "\nactiveProgram=" + glGetInteger(GL_CURRENT_PROGRAM)
                + "\ndrawFramebuffer=" + glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
                + "\nwrittenDepth=" + glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)
                + "\nwrittenDepthObjectType=" + glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
                + "\ndepthWrites=" + glGetBoolean(GL_DEPTH_WRITEMASK) + "\n");
        for (int i = 0; i < source.getDirectives().getDrawBuffers().length; i++) {
            int attachment = glGetInteger(GL_DRAW_BUFFER0 + i);
            receipt.append("writtenColorSlot=").append(i).append(" attachment=").append(attachment)
                    .append(" object=").append(attachment == GL_NONE ? 0 : glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)).append('\n');
        }
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        try {
            samplerLocations.forEach((name, location) -> {
                int unit = glGetUniformi(id, location);
                glActiveTexture(GL_TEXTURE0 + unit);
                receipt.append("sampled=").append(name).append(" unit=").append(unit)
                        .append(" texture2D=").append(glGetInteger(GL_TEXTURE_BINDING_2D)).append('\n');
            });
        } finally {
            glActiveTexture(activeTexture);
        }
        return receipt.toString();
    }

    void bind(Viewport<?> viewport, DhFrameState state) {
        glUseProgram(id);
        samplers.update();
        uniforms.update();
        customUniforms.push(this);
        images.update();
        matrix("iris_ProjectionMatrix", viewport.projection);
        matrix("iris_ProjectionMatrixInverse", inverse.set(viewport.projection).invert());
        matrix("iris_ModelViewMatrix", viewport.modelView);
        matrix("iris_ModelViewMatrixInverse", inverse.set(viewport.modelView).invert());
        matrix("dhProjection", state.projection);
        matrix("dhProjectionInverse", state.inverseProjection);
        matrix("dhPreviousProjection", state.previousProjection);
        int normal = locations.get("iris_NormalMatrix");
        if (normal >= 0) glUniformMatrix3fv(normal, false, normalMatrix.set(viewport.modelView).invert().transpose().get(normalValues));
        glUniform1f(locations.get("dhNearPlane"), state.projection.perspectiveNear());
        glUniform1f(locations.get("dhFarPlane"), state.projection.perspectiveFar());
        glUniform1i(locations.get("dhRenderDistance"), Math.round(VoxyConfig.CONFIG.sectionRenderDistance * 32 * 16));
        net.irisshaders.iris.gl.blending.BlendModeOverride.restore();
        source.getDirectives().getBlendModeOverride().ifPresent(override -> override.apply());
        for (var blend : bufferBlends) blend.apply();
    }

    private void matrix(String name, Matrix4f matrix) {
        int location = locations.get(name);
        if (location >= 0) glUniformMatrix4fv(location, false, matrix.get(matrixValues));
    }
}
