package me.cortex.voxy.client.iris;

import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

final class DhPrototypeFixtures {
    static ProgramSource source(String mode, ProgramSource original) {
        var stages = stages(mode);
        return new ProgramSource(original.getName() + "-" + mode, stages.vertex(), null, null, null, stages.fragment(),
                original.getParent(), ShaderProperties.empty(), null).withDirectiveOverride(original.getDirectives());
    }

    static DhPrototypeTransform.Stages stages(String mode) {
        String vertex = """
                #version 330 compatibility
                uniform mat4 dhProjection;
                varying vec4 probeColor;
                varying vec3 probeNormal;
                varying vec2 probeLight;
                flat varying int probeMaterial;
                void main() {
                    probeColor = gl_Color;
                    probeNormal = gl_Normal;
                    probeLight = gl_MultiTexCoord2.xy;
                    probeMaterial = dhMaterialId;
                    gl_Position = dhProjection * gl_ModelViewMatrix * gl_Vertex;
                }
                """;
        String expression = switch (mode) {
            case "identity" -> "probeColor";
            case "inputs" -> "vec4(probeNormal * 0.5 + 0.5, 1.0)";
            case "light" -> "vec4(probeLight, 0.0, 1.0)";
            case "material" -> "vec4(float(probeMaterial) / 15.0, float(probeMaterial == 12), float(probeMaterial == 1), 1.0)";
            case "depth" -> "vec4(vec3(gl_FragCoord.z), 1.0)";
            default -> throw new IllegalArgumentException("Unknown VOXY-003 observational fixture: " + mode);
        };
        String fragment = "#version 330 compatibility\nvarying vec4 probeColor;\nvarying vec3 probeNormal;\nvarying vec2 probeLight;\nflat varying int probeMaterial;\nvoid main() { gl_FragData[0] = " + expression + "; }\n";
        return new DhPrototypeTransform.Stages(vertex, fragment);
    }
}
