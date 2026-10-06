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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

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
    private final Matrix4f previousProjection = new Matrix4f();
    private final Matrix4f currentProjection = new Matrix4f();
    private int frame = -1;

    DhPrototypeProgram(int id, ProgramSource source, IrisRenderingPipeline pipeline, CustomUniforms customUniforms) {
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
        pipeline.addGbufferOrShadowSamplers(samplerBuilder, imageBuilder, pipeline::getFlippedAfterPrepare, false, false, true, false);
        samplers = samplerBuilder.build();
        images = imageBuilder.build();
        DhPrototype.write(source.getName() + "-bindings.txt", "uniformLocations=" + locations + "\nreservedVoxySamplers=[0,1,2]\n");
    }

    void bind(Viewport<?> viewport) {
        glUseProgram(id);
        samplers.update();
        uniforms.update();
        customUniforms.push(this);
        images.update();
        if (frame != viewport.frameId) {
            previousProjection.set(frame < 0 ? viewport.projection : currentProjection);
            currentProjection.set(viewport.projection);
            frame = viewport.frameId;
        }
        matrix("iris_ProjectionMatrix", viewport.projection);
        matrix("iris_ProjectionMatrixInverse", inverse.set(viewport.projection).invert());
        matrix("iris_ModelViewMatrix", viewport.modelView);
        matrix("iris_ModelViewMatrixInverse", inverse.set(viewport.modelView).invert());
        matrix("dhProjection", viewport.projection);
        matrix("dhProjectionInverse", inverse.set(viewport.projection).invert());
        matrix("dhPreviousProjection", previousProjection);
        int normal = locations.get("iris_NormalMatrix");
        if (normal >= 0) glUniformMatrix3fv(normal, false, normalMatrix.set(viewport.modelView).invert().transpose().get(normalValues));
        glUniform1f(locations.get("dhNearPlane"), viewport.projection.perspectiveNear());
        glUniform1f(locations.get("dhFarPlane"), viewport.projection.perspectiveFar());
        glUniform1i(locations.get("dhRenderDistance"), Math.round(VoxyConfig.CONFIG.sectionRenderDistance * 32));
        source.getDirectives().getBlendModeOverride().ifPresent(override -> override.apply());
        for (var blend : bufferBlends) blend.apply();
    }

    private void matrix(String name, Matrix4f matrix) {
        int location = locations.get(name);
        if (location >= 0) glUniformMatrix4fv(location, false, matrix.get(matrixValues));
    }
}
