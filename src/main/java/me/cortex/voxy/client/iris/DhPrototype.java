package me.cortex.voxy.client.iris;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.rendering.Viewport;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;

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
    public final ProgramSource terrain;
    public final ProgramSource water;
    public IrisVoxyRenderPipeline owner;
    private IrisRenderingPipeline pipeline;
    private CustomUniforms uniforms;
    private DhPrototypeProgram opaqueProgram;
    private DhPrototypeProgram waterProgram;
    private String receipt;
    private final boolean[] submitted = new boolean[2];

    private DhPrototype(ProgramSource terrain, ProgramSource water) {
        this.terrain = terrain;
        this.water = water;
    }

    public static boolean requested() { return !MODE.equals("off"); }
    public static boolean activeEnvironment() { return environmentActive; }

    public static void requireNoDh() {
        if (FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            throw new IllegalStateException("VOXY-003 disposable experiment requires DH absent; no coexistence policy is selected");
        }
    }

    public static ImmutableList<StringPair> environment(Path root, Map<String, String> options, ImmutableList<StringPair> environment) {
        environmentActive = false;
        if (!requested()) return environment;
        try (var paths = Files.walk(root)) {
            if (paths.anyMatch(path -> path.getFileName().toString().equals("voxy.json") && Files.isRegularFile(path))) return environment;
        } catch (IOException e) { throw new IllegalStateException("VOXY-003 cannot establish native metadata precedence", e); }
        requireNoDh();
        output = Path.of(System.getProperty("voxy.dhPrototype.output"));
        var builder = ImmutableList.<StringPair>builder().addAll(environment).add(new StringPair("DISTANT_HORIZONS", ""));
        var result = builder.build();
        try {
            StringBuilder manifest = new StringBuilder("mode=" + MODE + "\nroot=" + root + "\noptions=" + new TreeMap<>(options) + "\n");
            result.forEach(pair -> manifest.append("define=").append(pair.key()).append('=').append(pair.value()).append('\n'));
            try (var paths = Files.walk(root)) {
                for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    manifest.append("source=").append(root.relativize(path)).append(' ').append(hash(Files.readAllBytes(path))).append('\n');
                }
            }
            String text = manifest.toString();
            write("environment-" + hash(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".txt", text);
        } catch (IOException e) { throw new IllegalStateException("VOXY-003 cannot preserve source receipt", e); }
        environmentActive = true;
        return result;
    }

    public static DhPrototype from(ProgramSet set) {
        if (!environmentActive) throw new IllegalStateException("VOXY-003 pack was not loaded with the experimental environment");
        var terrain = set.get(ProgramId.DhTerrain).orElseThrow(() -> new IllegalStateException("VOXY-003 dh_terrain missing"));
        var water = set.get(ProgramId.DhWater).orElseThrow(() -> new IllegalStateException("VOXY-003 dh_water missing"));
        var probe = new DhPrototype(terrain, water);
        probe.receipt = hash((terrain.getVertexSource().orElseThrow() + terrain.getFragmentSource().orElseThrow()
                + water.getVertexSource().orElseThrow() + water.getFragmentSource().orElseThrow()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        probe.recordSource(terrain);
        probe.recordSource(water);
        write(probe.receipt + "-features.txt", "dh_shadow=" + set.get(ProgramId.DhShadow).isPresent()
                + "\nprototypePasses=terrain,water only; shadow feasibility NOT established\nmodelUploadStride=64; prototype uses unused bytes36..63; no extra atlas/depth allocation\n"
                + "inputColor=per-face stencil-covered average of existing captured RGBA; partial tint approximation\n"
                + "exact block atlas and ordinary customId retained; full texture fidelity NOT established by DH flat-color inputs\n");
        set.get(ProgramId.DhShadow).ifPresent(source -> { probe.recordSource(source); write(probe.receipt + "-shadow-gap.txt", "dh_shadow discovered; NOT executed by VOXY-003 terrain/water experiment\n"); });
        return probe;
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
        ProgramSource source = translucent ? water : terrain;
        if (!MODE.equals("pack")) source = DhPrototypeFixtures.source(MODE, source);
        long start = System.nanoTime();
        var stages = DhPrototypeTransform.transform(source, pipeline);
        long transformNanos = System.nanoTime() - start;
        write(receipt + "-" + source.getName() + "-transform.txt", "loadTimeTransformNanos=" + transformNanos + "\nvertexSha256="
                + hash(stages.vertex().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "\nfragmentSha256="
                + hash(stages.fragment().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "\n");
        write(receipt + "-" + source.getName() + ".generated.vert", stages.vertex());
        write(receipt + "-" + source.getName() + ".generated.frag", stages.fragment());
        return new Stages(stages.vertex(), stages.fragment());
    }

    public void attach(int terrainId, int waterId, long compileNanos) {
        opaqueProgram = new DhPrototypeProgram(terrainId, terrain, pipeline, uniforms);
        waterProgram = new DhPrototypeProgram(waterId, water, pipeline, uniforms);
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
                + "\nprojection=" + viewport.projection + "\nmodelView=" + viewport.modelView
                + "\nsubmitted=glMultiDrawElementsIndirectCountARB; execution/visual/cost acceptance NOT inferred\n");
    }

    public void close() {
        owner = null;
        opaqueProgram = null;
        waterProgram = null;
    }

    static void write(String file, String contents) {
        try { Files.createDirectories(output); Files.writeString(output.resolve(file), contents); }
        catch (IOException e) { throw new IllegalStateException("VOXY-003 receipt write failed", e); }
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
