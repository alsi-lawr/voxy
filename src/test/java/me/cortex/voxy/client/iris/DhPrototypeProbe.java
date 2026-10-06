package me.cortex.voxy.client.iris;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DhPrototypeProbe {
    public static void main(String[] args) throws Exception {
        checkFaceInputs();
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
