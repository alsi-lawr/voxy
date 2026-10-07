package me.cortex.voxy.client.iris;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class DhTranslationTest {
    public static void main(String[] args) throws Exception {
        cacheIdentityAndIsolation();
        failureReuse();
        faceInputs();
        if (args.length != 0) {
            Path output = Path.of(args[0]);
            Files.createDirectories(output);
            var config = net.irisshaders.iris.Iris.class.getDeclaredField("irisConfig");
            config.setAccessible(true);
            config.set(null, new net.irisshaders.iris.config.IrisConfig(output.resolve("unused-iris.properties")));
            translateFixtures(output);
            if (args.length > 1) translateCorpus(Path.of(args[1]), output.resolve("corpus"));
        }
        System.out.println("DH immutable cache reuse, identity invalidation, failure isolation and unsigned face input checks passed.");
    }

    private static void cacheIdentityAndIsolation() {
        var source = new DhTranslationCache.Source("vertex", null, null, null, "fragment");
        var textures = new Object2ObjectOpenHashMap<net.irisshaders.iris.helpers.Tri<String, net.irisshaders.iris.gl.texture.TextureType, net.irisshaders.iris.shaderpack.texture.TextureStage>, String>();
        var key = new DhTranslationCache.Key("environment", "/world0", "dh_terrain", source, textures);
        var cache = new DhTranslationCache();
        var calls = new AtomicInteger();
        var result = new DhPrototypeTransform.Stages("lowered vertex", "lowered fragment");
        java.util.function.Supplier<DhPrototypeTransform.Stages> translate = () -> { calls.incrementAndGet(); return result; };
        require(cache.get(key, translate) == result && cache.get(key, translate) == result && calls.get() == 1, "Repeated lookup must not retranslate");
        textures.put(new net.irisshaders.iris.helpers.Tri<>("sampler", net.irisshaders.iris.gl.texture.TextureType.TEXTURE_2D,
                net.irisshaders.iris.shaderpack.texture.TextureStage.GBUFFERS_AND_SHADOW), "mapped");
        cache.get(key, translate);
        require(calls.get() == 1, "Caller texture-map mutation must not corrupt the cached key");
        cache.get(new DhTranslationCache.Key("environment", "/world0", "dh_terrain", source, textures), translate);
        cache.get(new DhTranslationCache.Key("environment", "/world-1", "dh_terrain", source, Map.of()), translate);
        cache.get(new DhTranslationCache.Key("environment", "/world0", "dh_water", source, Map.of()), translate);
        cache.get(new DhTranslationCache.Key("environment", "/world0", "dh_terrain", new DhTranslationCache.Source("edited", null, null, null, "fragment"), Map.of()), translate);
        cache.get(new DhTranslationCache.Key("new environment", "/world0", "dh_terrain", source, Map.of()), translate);
        require(calls.get() == 6, "Source, texture, program, dimension and environment changes must invalidate independently");
        new DhTranslationCache().get(key, translate);
        require(calls.get() == 7, "Different pack owners must not share retained artifacts");
        var defines = List.of(new StringPair("DISTANT_HORIZONS", ""));
        String identity = DhShaderEnvironment.identity(Map.of("lib/include.glsl", "hash"), Map.of("QUALITY", "1"), defines, "contract");
        require(!identity.equals(DhShaderEnvironment.identity(Map.of("lib/include.glsl", "edited"), Map.of("QUALITY", "1"), defines, "contract")), "Included source changes must invalidate even unchanged effective stages");
        require(!identity.equals(DhShaderEnvironment.identity(Map.of("lib/include.glsl", "hash"), Map.of("QUALITY", "2"), defines, "contract")), "Options must invalidate");
        require(!identity.equals(DhShaderEnvironment.identity(Map.of("lib/include.glsl", "hash"), Map.of("QUALITY", "1"), List.of(), "contract")), "Environment must invalidate");
        require(!identity.equals(DhShaderEnvironment.identity(Map.of("lib/include.glsl", "hash"), Map.of("QUALITY", "1"), defines, "new contract")), "Pinned Iris/adapter contract must invalidate");
        require(!DhShaderEnvironment.fingerprint(List.of("ab", "c")).equals(DhShaderEnvironment.fingerprint(List.of("a", "bc"))), "Field concatenations must not alias");
    }

    private static void failureReuse() {
        var cache = new DhTranslationCache();
        var key = new DhTranslationCache.Key("pack", "/world1", "dh_water", new DhTranslationCache.Source("invalid", null, null, null, "fragment"), Map.of());
        var calls = new AtomicInteger();
        var failure = new DhTranslationFailure(DhTranslationFailure.Code.TRANSFORM_FAILED, "fixture", "/world1", "dh_water", "invalid source", null);
        for (int i = 0; i < 2; i++) {
            try { cache.get(key, () -> { calls.incrementAndGet(); throw failure; }); throw new AssertionError("Failure escaped cache"); }
            catch (DhTranslationFailure actual) { require(actual == failure, "Cached diagnostic must retain the failing identity"); }
        }
        require(calls.get() == 1, "Failed translation must not parse on every attempt");
        var validKey = new DhTranslationCache.Key("pack", "/world0", "dh_terrain", key.source(), Map.of());
        require(cache.get(validKey, () -> new DhPrototypeTransform.Stages("v", "f")) != null, "One dimension failure must not poison another");
        var stageKey = new DhTranslationCache.Key("pack", "/world1", "dh_water", new DhTranslationCache.Source("v", "g", null, null, "f"), Map.of());
        try { cache.translate("fixture", stageKey); throw new AssertionError("Unsupported stage silently dropped"); }
        catch (DhTranslationFailure actual) { require(actual.code() == DhTranslationFailure.Code.UNSUPPORTED_STAGE && actual.dimension().equals("/world1"), "Unsupported stage must identify selected dimension"); }
    }

    private static void faceInputs() {
        int[] colours = {0xff302010, 0x80504030, 0xffffffff};
        int[] depth = {1, 0x123400ff, 0x12340000};
        var texture = new me.cortex.voxy.client.core.model.ColourDepthTextureData(colours, depth, 3, 1);
        require(DhPrototypeInputs.faceColour(texture) == 0xbf403020 && colours[0] == 0xff302010 && depth[1] == 0x123400ff,
                "Stencil-covered unsigned channels must average without altering the existing bake input");
    }

    private static void translateFixtures(Path output) throws Exception {
        Path includeRoot = output.resolve("includes");
        Files.createDirectories(includeRoot.resolve("lib"));
        Files.writeString(includeRoot.resolve("lib/color.glsl"), "#define QUALITY 1 // [1 2]\nvec4 includedColor(vec4 color) { return color * float(QUALITY); }\n");
        var fixture = DhPrototypeFixtures.stages("identity");
        String vertex = fixture.vertex().replace("void main()", "#include \"/lib/color.glsl\"\nvoid main()").replace("probeColor = gl_Color", "probeColor = includedColor(gl_Color)");
        Files.writeString(includeRoot.resolve("dh_terrain.vsh"), vertex);
        var entry = AbsolutePackPath.fromAbsolutePath("/dh_terrain.vsh");
        var graph = new IncludeGraph(includeRoot, ImmutableList.of(entry));
        var options = new ShaderPackOptions(graph, Map.of("QUALITY", "2"));
        String preprocessed = JcppProcessor.glslPreprocessSource(String.join("\n", new IncludeProcessor(options.getIncludes()).getIncludedFile(entry)), List.of());
        write(output, "includes-options", DhPrototypeTransform.transform("dh_terrain", preprocessed, fixture.fragment(), new Object2ObjectOpenHashMap<>()));
        String collision = fixture.vertex().replace("void main()", "struct QuadData { float value; };\nfloat voxyDh0field = 0.25;\nfloat voxyPrototypeInit() { return 0.5; }\nfloat cameraSubPos = 0.125;\nvoid main()")
                .replace("probeColor = gl_Color", "probeColor = gl_Color + vec4(voxyDh0field + voxyPrototypeInit() + cameraSubPos)");
        write(output, "collisions-main", DhPrototypeTransform.transform("dh_terrain", collision, fixture.fragment(), new Object2ObjectOpenHashMap<>()));
        for (var mode : List.of("inputs", "light", "material")) {
            var stages = DhPrototypeFixtures.stages(mode);
            write(output, mode, DhPrototypeTransform.transform("dh_terrain", stages.vertex(), stages.fragment(), new Object2ObjectOpenHashMap<>()));
        }
        var cache = new DhTranslationCache();
        for (var bad : List.of("attribute vec3 unsupported; vec3 readInput() { return unsupported; }", "vec3 _vert_position;", "layout(std430, binding=1) buffer User { float value; };")) {
            String badVertex = fixture.vertex().replace("void main()", bad + "\nvoid main()");
            var key = new DhTranslationCache.Key("pack", "/world0", "dh_terrain", new DhTranslationCache.Source(badVertex, null, null, null, fixture.fragment()), Map.of());
            try { cache.translate("fixture", key); throw new AssertionError("Unsupported input was accepted: " + bad); }
            catch (DhTranslationFailure failure) { System.out.println("Expected owned failure: " + failure.getMessage()); }
        }
    }

    private static void translateCorpus(Path root, Path output) throws Exception {
        Files.createDirectories(output);
        int count = 0;
        var manifest = new ArrayList<String>();
        for (var pack : List.of("makeup-pack-88", "euphoria-mid-pack-88")) {
            try (var files = Files.list(root.resolve(pack).resolve("stages"))) {
                for (var vertex : files.filter(path -> path.toString().endsWith(".pre.vert")).sorted().toList()) {
                    String stem = vertex.getFileName().toString().replace(".pre.vert", "");
                    var fragment = vertex.resolveSibling(stem + ".pre.frag");
                    String program = stem.substring(stem.indexOf('-') + 1);
                    var source = new DhTranslationCache.Source(Files.readString(vertex), null, null, null, Files.readString(fragment));
                    var key = new DhTranslationCache.Key("frozen88", stem.substring(0, 64), program, source, Map.of());
                    var cache = new DhTranslationCache();
                    var stages = cache.translate(pack, key);
                    require(cache.translate(pack, key) == stages, "Actual corpus stages must reuse exact artifact");
                    write(output, pack + "-" + stem, stages);
                    manifest.add(vertex + " " + DhPrototype.hash(Files.readAllBytes(vertex)) + " " + DhPrototype.hash(Files.readAllBytes(fragment)));
                    count++;
                }
            }
        }
        Files.write(output.resolve("INPUTS.txt"), manifest);
        System.out.println("Transformed " + count + " original frozen effective corpus programs through pinned Iris + packed GPU lowering; no GL execution.");
    }

    private static void write(Path output, String name, DhPrototypeTransform.Stages stages) throws Exception {
        Files.writeString(output.resolve(name + ".vert"), stages.vertex());
        Files.writeString(output.resolve(name + ".frag"), stages.fragment());
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
