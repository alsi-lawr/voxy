package me.cortex.voxy.client.iris;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.rendering.Viewport;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import me.cortex.voxy.client.mixin.iris.IrisRenderingPipelineAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class DhPrototype {
    private static final String MODE = System.getProperty("voxy.dhPrototype", "off");
    private static volatile boolean environmentActive;
    private static Path output;
    private static DhShaderEnvironment loadedEnvironment;
    private final DhShaderEnvironment environment;
    private final String dimension;
    public final ProgramSource terrain;
    public final ProgramSource water;
    public IrisVoxyRenderPipeline owner;
    private IrisRenderingPipeline pipeline;
    private CustomUniforms uniforms;
    private DhPrototypeProgram opaqueProgram;
    private DhPrototypeProgram waterProgram;
    private String receipt;
    private Stages terrainStages;
    private Stages waterStages;
    private final boolean[] submitted = new boolean[2];

    DhPrototype(ProgramSource terrain, ProgramSource water, DhShaderEnvironment environment, String dimension) {
        this.environment = environment;
        this.dimension = dimension;
        this.terrain = terrain;
        this.water = water;
    }

    public static boolean requested() { return !MODE.equals("off"); }
    public static boolean activeEnvironment() { return environmentActive; }
    public static DhShaderEnvironment loadedEnvironment() { return loadedEnvironment; }

    public static void requireNoDh() {
        if (FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            throw new DhTranslationFailure(DhTranslationFailure.Code.LOD_ENGINE_CONFLICT,
                    loadedEnvironment == null ? "loading" : loadedEnvironment.pack(), "loading", "adapter",
                    "Distant Horizons is installed; concurrent LOD rendering is not supported", null);
        }
    }

    public static ImmutableList<StringPair> environment(Path root, Map<String, String> options, ImmutableList<StringPair> environment) {
        environmentActive = false;
        loadedEnvironment = null;
        output = null;
        if (!requested()) return environment;
        try (var paths = Files.walk(root)) {
            if (paths.anyMatch(path -> path.getFileName().toString().equals("voxy.json") && Files.isRegularFile(path))) return environment;
        } catch (IOException e) { throw new IllegalStateException("VOXY-003 cannot establish native metadata precedence", e); }
        requireNoDh();
        String outputPath = System.getProperty("voxy.dhPrototype.output");
        if (outputPath != null) output = Path.of(outputPath);
        var builder = ImmutableList.<StringPair>builder().addAll(environment).add(new StringPair("DISTANT_HORIZONS", ""));
        var result = builder.build();
        try {
            loadedEnvironment = DhShaderEnvironment.snapshot(root, options, result);
            if (output != null) {
                StringBuilder manifest = new StringBuilder("mode=" + MODE + "\nroot=" + loadedEnvironment.pack() + "\noptions=" + new TreeMap<>(options) + "\n");
                result.forEach(pair -> manifest.append("define=").append(pair.key()).append('=').append(pair.value()).append('\n'));
                try (var paths = Files.walk(root)) {
                    for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
                        manifest.append("source=").append(root.relativize(path)).append(' ').append(hash(Files.readAllBytes(path))).append('\n');
                    }
                }
                String text = manifest.toString();
                write("environment-" + hash(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".txt", text + "identity=" + loadedEnvironment.identity() + "\n");
            }
        } catch (IOException e) { throw new IllegalStateException("VOXY-003 cannot preserve source receipt", e); }
        environmentActive = true;
        return result;
    }

    public static DhPrototype from(ProgramSet set, String dimension) {
        var environment = ((IGetDhShaderEnvironment)set.getPack()).voxy$getDhShaderEnvironment();
        if (environment == null) throw new IllegalStateException("DH source environment was not captured for this shader pack");
        var terrain = required(set, ProgramId.DhTerrain, environment, dimension);
        var water = required(set, ProgramId.DhWater, environment, dimension);
        var probe = new DhPrototype(terrain, water, environment, dimension);
        if (set.get(ProgramId.DhShadow).isPresent()) {
            throw new DhTranslationFailure(DhTranslationFailure.Code.SHADOW_PASS_UNAVAILABLE, environment.pack(), dimension,
                    "dh_shadow", "authored DH shadows require the pending shadow pass integration; no shadowless substitute", null);
        }
        probe.receipt = DhShaderEnvironment.fingerprint(java.util.List.of(environment.identity(), dimension,
                terrain.getVertexSource().orElseThrow(), terrain.getFragmentSource().orElseThrow(),
                water.getVertexSource().orElseThrow(), water.getFragmentSource().orElseThrow()));
        probe.recordSource(terrain);
        probe.recordSource(water);
        write(probe.receipt + "-features.txt", "dh_shadow=" + set.get(ProgramId.DhShadow).isPresent()
                + "\nprototypePasses=terrain,water only; shadow feasibility NOT established\nmodelUploadStride=64; prototype uses unused bytes36..63; no extra atlas/depth allocation\n"
                + "inputColor=per-face stencil-covered average of existing captured RGBA; partial tint approximation\n"
                + "exact block atlas and ordinary customId retained; full texture fidelity NOT established by DH flat-color inputs\n");
        return probe;
    }

    private static ProgramSource required(ProgramSet set, ProgramId program, DhShaderEnvironment environment, String dimension) {
        return set.get(program).orElseThrow(() -> new DhTranslationFailure(DhTranslationFailure.Code.MISSING_PROGRAM,
                environment.pack(), dimension, program == ProgramId.DhTerrain ? "dh_terrain" : "dh_water",
                "selected dimension must provide its vertex and fragment stages", null));
    }

    private void recordSource(ProgramSource source) {
        write(receipt + "-" + source.getName() + ".pre.vert", source.getVertexSource().orElseThrow());
        write(receipt + "-" + source.getName() + ".pre.frag", source.getFragmentSource().orElseThrow());
        write(receipt + "-" + source.getName() + "-directives.txt", "targets=" + java.util.Arrays.toString(source.getDirectives().getDrawBuffers())
                + "\nblend=" + source.getDirectives().getBlendModeOverride() + "\nbufferBlend=" + source.getDirectives().getBufferBlendOverrides() + "\n");
    }

    public void bindPipeline(IrisRenderingPipeline pipeline, CustomUniforms uniforms) {
        this.pipeline = pipeline;
        this.uniforms = uniforms;
    }

    public record Stages(String vertex, String fragment) {}
    public Stages stages(boolean translucent) {
        return java.util.Objects.requireNonNull(translucent ? waterStages : terrainStages,
                "Selected DH sources must be lowered during Iris pipeline load");
    }

    void prepareStages(Map<Tri<String, TextureType, TextureStage>, String> textures) {
        var terrain = translate(this.terrain, textures);
        var water = translate(this.water, textures);
        this.terrainStages = terrain;
        this.waterStages = water;
    }

    private Stages translate(ProgramSource source, Map<Tri<String, TextureType, TextureStage>, String> textures) {
        if (!MODE.equals("pack")) source = DhPrototypeFixtures.source(MODE, source);
        long start = System.nanoTime();
        var key = new DhTranslationCache.Key(environment.identity(), dimension, source.getName(),
                DhTranslationCache.Source.from(source), textures);
        var stages = environment.translations().translate(environment.pack(), key);
        long transformNanos = System.nanoTime() - start;
        write(receipt + "-" + source.getName() + "-transform.txt", "environmentIdentity=" + environment.identity() + "\ndimension=" + dimension + "\nloadTimeTransformOrCacheLookupNanos=" + transformNanos + "\nvertexSha256="
                + hash(stages.vertex().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "\nfragmentSha256="
                + hash(stages.fragment().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "\n");
        write(receipt + "-" + source.getName() + ".generated.vert", stages.vertex());
        write(receipt + "-" + source.getName() + ".generated.frag", stages.fragment());
        return new Stages(stages.vertex(), stages.fragment());
    }

    public void attach(int terrainId, int waterId, long compileNanos) {
        opaqueProgram = new DhPrototypeProgram(terrainId, terrain, pipeline, uniforms, false);
        waterProgram = new DhPrototypeProgram(waterId, water, pipeline, uniforms, true);
        write(receipt + "-link.txt", "terrain=" + terrainId + " water=" + waterId + " linked by Voxy Shader.compile\ncompileNanos=" + compileNanos + "\n");
    }

    public void bind(boolean translucent, Viewport<?> viewport) {
        requireNoDh();
        (translucent ? waterProgram : opaqueProgram).bind(viewport);
    }

    public void recordSubmission(boolean translucent, Viewport<?> viewport) {
        int slot = translucent ? 1 : 0;
        if (submitted[slot]) return;
        submitted[slot] = true;
        write(receipt + "-" + (translucent ? "water" : "terrain") + "-submission.txt", "mode=" + MODE
                + "\nframe=" + viewport.frameId + "\nsize=" + viewport.width + "x" + viewport.height
                + "\nopaqueDepth=" + owner.fb.getDepthTex().id + "\nwaterDepth=" + owner.fbTranslucent.getDepthTex().id
                + "\nopaqueTargets=" + java.util.Arrays.toString(owner._getData().opaqueDrawTargets)
                + "\nwaterTargets=" + java.util.Arrays.toString(owner._getData().translucentDrawTargets)
                + "\nplacement=immediate Voxy water after opaque; NOT Iris post-translucent DH placement"
                + "\nirisPhase=" + pipeline.getPhase()
                + "\nsamplerFlipSupplier=getFlippedAfterPrepare; attachmentFlipSet=getFlippedAfterPrepare"
                + "\nprepareFlips=" + pipeline.getFlippedAfterPrepare() + "\nafterTranslucentFlips=" + pipeline.getFlippedAfterTranslucent()
                + "\nwaterDhDepthMeaning=completed opaque fb for dhDepthTex/0/1; water-inclusive reference semantics NOT established"
                + "\nlaterIrisDhDepthMeaning=dhDepthTex/0 final fbTranslucent; dhDepthTex1 opaque fb\n"
                + (translucent ? waterProgram : opaqueProgram).resourceBindings() + targetInventory()
                + "\nprojection=" + viewport.projection + "\nmodelView=" + viewport.modelView
                + "\nsubmitted=glMultiDrawElementsIndirectCountARB; execution/visual/cost acceptance NOT inferred\n");
    }

    private String targetInventory() {
        var targets = ((IrisRenderingPipelineAccessor)pipeline).getRenderTargets();
        var receipt = new StringBuilder();
        for (int i = 0; i < targets.getRenderTargetCount(); i++) {
            var target = targets.get(i);
            if (target == null) continue;
            receipt.append("irisTarget=").append(i).append(" main=").append(target.getMainTexture())
                    .append(" alt=").append(target.getAltTexture()).append(" format=").append(target.getInternalFormat())
                    .append(" size=").append(target.getWidth()).append('x').append(target.getHeight()).append('\n');
        }
        return receipt.toString();
    }

    public void close() {
        owner = null;
        opaqueProgram = null;
        waterProgram = null;
    }

    static void write(String file, String contents) {
        if (output == null) return;
        try { Files.createDirectories(output); Files.writeString(output.resolve(file), contents); }
        catch (IOException e) { throw new IllegalStateException("VOXY-003 receipt write failed", e); }
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
