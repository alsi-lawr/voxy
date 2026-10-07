package me.cortex.voxy.client.iris;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class DhTranslationTest {
    public static void main(String[] args) throws Exception {
        frameHistory();
        cacheIdentityAndIsolation();
        failureReuse();
        faceInputs();
        Path scratch = Files.createTempDirectory(Path.of("build"), "dh-translation-test-");
        try {
            var config = net.irisshaders.iris.Iris.class.getDeclaredField("irisConfig");
            config.setAccessible(true);
            config.set(null, new net.irisshaders.iris.config.IrisConfig(scratch.resolve("unused-iris.properties")));
            selectedPreflight(scratch);
            diagnosticOrigins(scratch);
            if (args.length != 0) {
                Path output = Path.of(args[0]);
                Files.createDirectories(output);
                translateFixtures(output);
                if (args.length > 1) translateCorpus(Path.of(args[1]), output.resolve("corpus"));
            }
        } finally {
            try (var paths = Files.walk(scratch)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("DH selected preflight, provider-origin failure reuse, immutable cache identity/isolation and unsigned face input checks passed.");
    }

    private static void frameHistory() {
        var state = new DhFrameState();
        var first = new org.joml.Matrix4f().perspective(1f, 1.8f, 16f, 8192f);
        var second = new org.joml.Matrix4f().perspective(.9f, 1.8f, 16f, 8192f);
        state.update(1, 1600, 900, 0, 64, 0, first);
        require(state.previousProjection.equals(first), "First frame has no stale history");
        state.update(2, 1600, 900, 1, 64, 0, second);
        require(state.previousProjection.equals(first) && state.projection.equals(second), "Current and previous frame remain coherent");
        state.update(2, 1600, 900, 1, 64, 0, first);
        require(state.previousProjection.equals(first) && state.projection.equals(second), "Repeated pass cannot advance or overwrite frame history");
        var identity = new org.joml.Matrix4f(state.projection).mul(state.inverseProjection);
        require(identity.equals(new org.joml.Matrix4f(), .001f), "Inverse belongs to the same frame");
        state.update(3, 1280, 720, 1, 64, 0, second);
        require(state.previousProjection.equals(second) && state.resetReason.equals("resize"), "Resize resets history");
        state.update(4, 1280, 720, 1024, 64, 0, first);
        require(state.previousProjection.equals(first) && state.resetReason.equals("camera_discontinuity"), "Teleport resets history");
        state.update(7, 1280, 720, 1024, 64, 0, second);
        require(state.previousProjection.equals(second) && state.resetReason.equals("frame_gap"), "Missing render frames reset history");
        state.reset();
        state.update(7, 1280, 720, 1024, 64, 0, first);
        require(state.previousProjection.equals(first) && state.resetReason.equals("renderer_owner"), "New dimension/renderer owner resets even a reused frame ID");
        var unready = new org.joml.Matrix4f(first).m00(Float.NaN);
        state.update(8, 1280, 720, 1024, 64, 0, unready);
        require(!state.projection.isFinite(), "Upstream unready current projection is not replaced by a fallback");
        state.update(9, 1280, 720, 1024, 64, 0, second);
        require(state.historyReset && state.previousProjection.equals(second) && state.resetReason.equals("projection_unready"),
                "First valid frame after an unready projection cannot inherit invalid history");
        state.update(9, 1280, 720, 1024, 64, 0, first);
        require(state.historyReset && state.projection.equals(second) && state.previousProjection.equals(second),
                "Repeated pass cannot overwrite the recovered frame or advance its history");
        state.update(10, 1280, 720, 1024, 64, 0, first);
        require(!state.historyReset && state.previousProjection.equals(second), "Ordinary history resumes after recovery");
        state.reset();
        state.update(1, 1600, 900, 0, 64, 0, unready);
        state.update(2, 1600, 900, 0, 64, 0, first);
        require(state.historyReset && state.previousProjection.equals(first)
                && state.projection.mul(state.inverseProjection, new org.joml.Matrix4f()).equals(new org.joml.Matrix4f(), .001f),
                "Initial unready frame cannot poison the first valid current/inverse/previous tuple");
    }

    private static void selectedPreflight(Path scratch) throws Exception {
        Path root = Files.createDirectory(scratch.resolve("preflight"));
        var fixture = DhPrototypeFixtures.stages("identity");
        Files.writeString(root.resolve("dh_terrain.vsh"), fixture.vertex());
        var environment = DhShaderEnvironment.snapshot(root, Map.of(), List.of());
        var terrain = program("dh_terrain", fixture);
        var water = program("dh_water", fixture);
        var prototype = new DhPrototype(terrain, water, environment, "/world0");
        var textures = new Object2ObjectOpenHashMap<net.irisshaders.iris.helpers.Tri<String, net.irisshaders.iris.gl.texture.TextureType,
                net.irisshaders.iris.shaderpack.texture.TextureStage>, String>();
        textures.put(new net.irisshaders.iris.helpers.Tri<>("probeSampler", net.irisshaders.iris.gl.texture.TextureType.TEXTURE_2D,
                net.irisshaders.iris.shaderpack.texture.TextureStage.GBUFFERS_AND_SHADOW), "mappedSampler");
        prototype.prepareStages(textures);
        var opaque = prototype.stages(false);
        var translucent = prototype.stages(true);
        for (var source : List.of(terrain, water)) {
            var key = new DhTranslationCache.Key(environment.identity(), "/world0", source.getName(), DhTranslationCache.Source.from(source), textures);
            var cached = environment.translations().get(key, () -> { throw new AssertionError("Preflight must cache both selected sources"); });
            var stages = source == terrain ? opaque : translucent;
            require(stages.vertex() == cached.vertex() && stages.fragment() == cached.fragment(), "Renderer must consume the preflight artifact");
        }
        textures.clear();
        require(prototype.stages(false) == opaque && prototype.stages(true) == translucent,
                "Renderer construction must not re-key against later texture-map changes");
        var rejectedWater = program("dh_water", unsupportedInput(fixture));
        var rejected = new DhPrototype(terrain, rejectedWater, environment, "/world0");
        DhTranslationFailure first = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try { rejected.prepareStages(Map.of()); throw new AssertionError("Unsupported selected water escaped preflight"); }
            catch (DhTranslationFailure failure) {
                require(failure.code() == DhTranslationFailure.Code.UNSUPPORTED_VERTEX_INPUT && failure.program().equals("dh_water")
                        && failure.dimension().equals("/world0"), "Water rejection must retain selected context");
                if (first == null) first = failure;
                else require(first == failure, "Repeated preflight must reuse its owned failure");
            }
        }
        var authoredShadow = program("dh_shadow", new DhPrototypeTransform.Stages(
                "#version 330 compatibility\nvoid main() { gl_Position=ftransform(); }\n",
                "#version 330 compatibility\n/* DRAWBUFFERS:01 */\nvoid main() { gl_FragData[0]=vec4(0,1,0,1); gl_FragData[1]=vec4(0); }\n"));
        var shadowed = new DhPrototype(terrain, water, authoredShadow, environment, "/world0");
        shadowed.prepareStages(Map.of());
        var shadowKey = new DhTranslationCache.Key(environment.identity(), "/world0", "dh_shadow", DhTranslationCache.Source.from(authoredShadow), Map.of());
        var cachedShadow = environment.translations().get(shadowKey, () -> { throw new AssertionError("Shadow escaped selected load-time caching"); });
        require(shadowed.shadowStages().vertex() == cachedShadow.vertex(), "Shadow renderer uses lowered selected source without late translation");
        var disabledShadow = new DhPrototype(terrain, water, authoredShadow, environment, "/world0", false);
        disabledShadow.prepareStages(Map.of());
        require(disabledShadow.shadowStages() == null, "Disabled authored shadow has no renderer stages or late compilation");
        var badShadow = new DhPrototype(terrain, water, program("dh_shadow", unsupportedInput(fixture)), environment, "/world0");
        try { badShadow.prepareStages(Map.of()); throw new AssertionError("Unsupported shadow escaped load preflight"); }
        catch (DhTranslationFailure failure) {
            require(failure.program().equals("dh_shadow") && failure.code() == DhTranslationFailure.Code.UNSUPPORTED_VERTEX_INPUT,
                    "Owned shadow failure identifies the selected program");
        }
        System.out.println("Both selected sources cached before renderer access; unsupported water rejects during preflight and reuses the same failure.");
    }

    private static void diagnosticOrigins(Path scratch) throws Exception {
        Path directory = Files.createDirectories(scratch.resolve("directory/nested/shaders"));
        var fixture = unsupportedInput(DhPrototypeFixtures.stages("identity"));
        Files.writeString(directory.resolve("dh_terrain.vsh"), fixture.vertex());
        var archives = new ArrayList<java.nio.file.FileSystem>();
        try {
            var roots = new ArrayList<Path>();
            for (var name : List.of("first.zip", "second.zip")) {
                var archive = java.nio.file.FileSystems.newFileSystem(java.net.URI.create("jar:" + scratch.resolve(name).toUri()), Map.of("create", "true"));
                archives.add(archive);
                Path root = Files.createDirectories(archive.getPath("nested/shaders"));
                Files.writeString(root.resolve("dh_terrain.vsh"), fixture.vertex());
                roots.add(root);
            }
            roots.add(directory);
            String identity = null;
            for (var root : roots) {
                var environment = DhShaderEnvironment.snapshot(root, Map.of(), List.of());
                if (identity == null) identity = environment.identity();
                else require(identity.equals(environment.identity()), "Identical content must exercise separate owners with the same source identity");
                var prototype = new DhPrototype(program("dh_terrain", fixture), program("dh_water", DhPrototypeFixtures.stages("identity")), environment, "/world-1");
                DhTranslationFailure first = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    try { prototype.prepareStages(Map.of()); throw new AssertionError("Unsupported terrain escaped origin diagnostic"); }
                    catch (DhTranslationFailure failure) {
                        Path reported = Path.of(java.net.URI.create(failure.pack()));
                        require(reported.getFileSystem() == root.getFileSystem() && Files.isSameFile(reported, root)
                                && Files.readString(reported.resolve("dh_terrain.vsh")).equals(fixture.vertex()),
                                "Diagnostic must resolve to its own actual pack/provider and inner shader root");
                        require(failure.code() == DhTranslationFailure.Code.UNSUPPORTED_VERTEX_INPUT && failure.program().equals("dh_terrain")
                                && failure.dimension().equals("/world-1"), "Origin diagnostics must retain selected rejection context");
                        if (first == null) first = failure;
                        else require(first == failure, "Failure cache must retain each pack's origin independently");
                    }
                }
                System.out.println("Resolved owned cached rejection: " + first.getMessage());
            }
        } finally {
            for (var archive : archives) archive.close();
        }
    }

    private static ProgramSource program(String name, DhPrototypeTransform.Stages stages) {
        return new ProgramSource(name, stages.vertex(), null, null, null, stages.fragment(), null, null, null);
    }

    private static DhPrototypeTransform.Stages unsupportedInput(DhPrototypeTransform.Stages fixture) {
        return new DhPrototypeTransform.Stages(fixture.vertex().replace("void main()", "attribute vec3 unsupported;\nvoid main()")
                .replace("probeNormal = gl_Normal", "probeNormal = unsupported"), fixture.fragment());
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
        Path shadowRoot = Path.of("src/test/resources/dh-frame-shadow/shaders");
        for (var program : List.of("dh_terrain", "dh_water", "dh_shadow")) {
            var vertexEntry = AbsolutePackPath.fromAbsolutePath("/" + program + ".vsh");
            var fragmentEntry = AbsolutePackPath.fromAbsolutePath("/" + program + ".fsh");
            var includes = new IncludeProcessor(new IncludeGraph(shadowRoot, ImmutableList.of(vertexEntry, fragmentEntry)));
            String v = JcppProcessor.glslPreprocessSource(String.join("\n", includes.getIncludedFile(vertexEntry)), List.of());
            String f = JcppProcessor.glslPreprocessSource(String.join("\n", includes.getIncludedFile(fragmentEntry)), List.of());
            write(output, "frame-shadow-" + program, DhPrototypeTransform.transform(program, v, f, new Object2ObjectOpenHashMap<>()));
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
