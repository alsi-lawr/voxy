package me.cortex.voxy.client.iris;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.TextureType;

public final class DhPrototypeProbe {
    public static void main(String[] args) throws Exception {
        checkFaceInputs();
        checkWaterDepthIsolation();
        Path output = Path.of(args[0]);
        Path reference = Path.of(args[1]);
        Files.createDirectories(output);
        try (var paths = Files.list(reference)) {
            for (var vertex : paths.filter(path -> path.getFileName().toString().matches(".*_dh_(terrain|water)\\.vsh")).sorted().toList()) {
                String name = vertex.getFileName().toString().replace(".vsh", "");
                Files.writeString(output.resolve(name + ".vert"), DhPrototypeTransform.lower(Files.readString(vertex)));
                Files.copy(reference.resolve(name + ".fsh"), output.resolve(name + ".frag"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        for (var mode : new String[]{"identity", "inputs", "light", "material", "depth"}) {
            var fixture = DhPrototypeFixtures.stages(mode);
            Files.writeString(output.resolve(mode + ".dh.vert"), fixture.vertex());
            Files.writeString(output.resolve(mode + ".dh.frag"), fixture.fragment());
        }
        System.out.println("AST lowering executed on root-captured real Iris DH terrain/water stages; no Minecraft or GPU execution in this probe.");
    }

    private static void checkWaterDepthIsolation() {
        int[] opaque = {17};
        int[] total = {29};
        var waterBindings = new RecordedSamplers();
        var laterBindings = new RecordedSamplers();
        var water = VoxySamplers.waterDepthSamplers(waterBindings, () -> opaque[0]);
        water.addDynamicSampler(TextureType.TEXTURE_2D, () -> total[0], null, "dhDepthTex", "dhDepthTex0");
        water.addDynamicSampler(TextureType.TEXTURE_2D, () -> opaque[0], (ValueUpdateNotifier)null, null, "dhDepthTex1");
        water.addDynamicSampler(TextureType.TEXTURE_2D, () -> 43, null, "colortex4");
        laterBindings.addDynamicSampler(TextureType.TEXTURE_2D, () -> total[0], null, "dhDepthTex", "dhDepthTex0");
        laterBindings.addDynamicSampler(TextureType.TEXTURE_2D, () -> opaque[0], null, "dhDepthTex1");
        if (waterBindings.texture("dhDepthTex") != 17 || waterBindings.texture("dhDepthTex0") != 17
                || waterBindings.texture("dhDepthTex1") != 17 || waterBindings.texture("colortex4") != 43) {
            throw new AssertionError("Water depth registration did not isolate original DH reads from the written total-depth image");
        }
        opaque[0] = 53;
        total[0] = 71;
        if (waterBindings.texture("dhDepthTex") != 53 || laterBindings.texture("dhDepthTex") != 71
                || laterBindings.texture("dhDepthTex0") != 71 || laterBindings.texture("dhDepthTex1") != 53) {
            throw new AssertionError("Depth replacement/resize leaked water semantics into later Iris aliases or retained stale texture IDs");
        }
        System.out.println("Water DH-depth registration isolates sampled/written resources, follows replacement IDs and preserves later total/opaque alias distinction; headless supplier behavior only.");
    }

    private static final class RecordedSamplers implements SamplerHolder {
        private final Map<String, IntSupplier> textures = new HashMap<>();
        int texture(String name) { return textures.get(name).getAsInt(); }
        @Override
        public void addExternalSampler(int unit, String... names) { throw new AssertionError("Unexpected external registration"); }
        @Override
        public boolean hasSampler(String name) { return textures.containsKey(name); }
        @Override
        public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
            return addDynamicSampler(type, texture, notifier, sampler, names);
        }
        @Override
        public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
            return addDynamicSampler(type, texture, null, sampler, names);
        }
        @Override
        public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
            for (String name : names) textures.put(name, texture);
            return true;
        }
    }

    private static void checkFaceInputs() {
        int[] colour = {0xff302010, 0x80504030, 0xffffffff};
        int[] depth = {1, 0x123400ff, 0x12340000};
        var input = new me.cortex.voxy.client.core.model.ColourDepthTextureData(colour, depth, 3, 1);
        if (DhPrototypeInputs.faceColour(input) != 0xbf403020) throw new AssertionError("Unsigned RGBA/stencil-covered face average changed");
        if (colour[0] != 0xff302010 || depth[1] != 0x123400ff) throw new AssertionError("Prototype averaging mutated captured bake inputs");
        var empty = new me.cortex.voxy.client.core.model.ColourDepthTextureData(new int[]{-1}, new int[]{0x12340000}, 1, 1);
        if (DhPrototypeInputs.faceColour(empty) != 0) throw new AssertionError("Uncovered face must have zero input colour");
        System.out.println("Face-input unsigned-channel averaging, stencil coverage, empty face and input-preservation checks passed.");
    }
}
