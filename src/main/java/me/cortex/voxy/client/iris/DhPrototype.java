package me.cortex.voxy.client.iris;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.rendering.Viewport;
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
    public final ProgramSource shadow;
    private final boolean shadowEnabled;
    public final DhFrameState frameState = new DhFrameState();
    private Stages shadowStages;
    private Program shadowProgram;
    private net.irisshaders.iris.gl.framebuffer.GlFramebuffer shadowFramebuffer;
    public IrisVoxyRenderPipeline owner;
    private IrisRenderingPipeline pipeline;
    private CustomUniforms uniforms;
    private Program opaqueProgram;
    private Program waterProgram;
    private String receipt;
    private boolean closed;
    private DhTranslationFailure activationFailure;
    private Stages terrainStages;
    private Stages waterStages;
    private boolean shadowSubmitted;
    private boolean shadowWaterSubmitted;
    private int frameReceipts;
    private static final boolean FRAME_RECEIPTS = Boolean.getBoolean("voxy.dhPrototype.frameReceipts");
    public static final boolean SHADOW_COUNT_RECEIPTS = Boolean.getBoolean("voxy.dhPrototype.shadowCountReceipts");
    private int shadowCountReceipts;
    private int shadowCountFrames;
    private final boolean[] submitted = new boolean[2];

    DhPrototype(ProgramSource terrain, ProgramSource water, DhShaderEnvironment environment, String dimension) {
        this(terrain, water, null, environment, dimension);
    }

    DhPrototype(ProgramSource terrain, ProgramSource water, ProgramSource shadow, DhShaderEnvironment environment, String dimension) {
        this(terrain, water, shadow, environment, dimension, shadow != null);
    }

    DhPrototype(ProgramSource terrain, ProgramSource water, ProgramSource shadow, DhShaderEnvironment environment, String dimension, boolean shadowEnabled) {
        this.shadow = shadow;
        this.shadowEnabled = shadowEnabled;
        this.environment = environment;
        this.dimension = dimension;
        this.terrain = terrain;
        this.water = water;
    }

    public static boolean requested() { return !MODE.equals("off"); }
    public static boolean activeEnvironment() { return environmentActive; }
    public static DhShaderEnvironment loadedEnvironment() { return loadedEnvironment; }

    public static void clearEnvironment() {
        environmentActive = false;
        if (loadedEnvironment != null) loadedEnvironment.translations().clear();
        loadedEnvironment = null;
        output = null;
    }

    public static void requireNoDh() {
        if (me.cortex.voxy.client.core.util.LodRenderOwnership.dhActive()) {
            throw new DhTranslationFailure(DhTranslationFailure.Code.LOD_ENGINE_CONFLICT,
                    loadedEnvironment == null ? "loading" : loadedEnvironment.pack(), "loading", "adapter",
                    "Distant Horizons rendering is active; select only one LOD renderer", null);
        }
    }

    public static ImmutableList<StringPair> environment(Path root, Map<String, String> options, ImmutableList<StringPair> environment) {
        clearEnvironment();
        if (!requested()) return environment;
        try (var paths = Files.walk(root)) {
            if (paths.anyMatch(path -> path.getFileName().toString().equals("voxy.json") && Files.isRegularFile(path))) return environment;
        } catch (IOException e) {
            throw new DhTranslationFailure(DhTranslationFailure.Code.RELOAD_FAILED, root.toUri().toString(), "loading", "native_metadata",
                    "cannot establish native metadata precedence", e);
        }
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
        } catch (IOException e) {
            throw new DhTranslationFailure(DhTranslationFailure.Code.RELOAD_FAILED, root.toUri().toString(), "loading", "adapter",
                    "cannot snapshot selected shader sources", e);
        }
        environmentActive = true;
        return result;
    }

    public static DhPrototype from(ProgramSet set, String dimension) {
        var environment = ((IGetDhShaderEnvironment)set.getPack()).voxy$getDhShaderEnvironment();
        if (environment == null) throw new IllegalStateException("DH source environment was not captured for this shader pack");
        var terrain = required(set, ProgramId.DhTerrain, environment, dimension);
        var water = required(set, ProgramId.DhWater, environment, dimension);
        var shadow = set.get(ProgramId.DhShadow).orElse(null);
        boolean shadowEnabled = shadow != null && set.getPackDirectives().getShadowDirectives().isDhShadowEnabled().orElse(false)
                && set.getPackDirectives().getShadowDirectives().isShadowEnabled().orElse(true);
        var probe = new DhPrototype(terrain, water, shadow, environment, dimension, shadowEnabled);
        probe.receipt = DhShaderEnvironment.fingerprint(java.util.List.of(environment.identity(), dimension,
                terrain.getVertexSource().orElseThrow(), terrain.getFragmentSource().orElseThrow(),
                water.getVertexSource().orElseThrow(), water.getFragmentSource().orElseThrow()));
        probe.recordSource(terrain);
        probe.recordSource(water);
        if (probe.shadow != null) probe.recordSource(probe.shadow);
        write(probe.receipt + "-features.txt", "dh_shadow=" + set.get(ProgramId.DhShadow).isPresent()
                + "\ndh_shadow_enabled=" + probe.shadowEnabled + "\nadapterPasses=terrain,water,authored shadow when selected\nmodelUploadStride=64; adapter inputs occupy bytes36..63; two existing main D24S8 attachments plus optional shadow viewport; no extra atlas\n"
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
        if (this.pipeline != null || ready()) throw new IllegalStateException("DH candidate already belongs to a live Iris pipeline");
        closed = false;
        activationFailure = null;
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
        if (shadow != null) this.shadowStages = translate(shadow, textures);
    }

    private Stages translate(ProgramSource source, Map<Tri<String, TextureType, TextureStage>, String> textures) {
        if (!MODE.equals("pack") && source != shadow) source = DhPrototypeFixtures.source(MODE, source);
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

    public boolean shadowEnabled() { return shadowEnabled; }
    public Stages shadowStages() { return shadowEnabled ? shadowStages : null; }

    public void beginFrame(Viewport<?> viewport) {
        frameState.update(viewport.frameId, viewport.width, viewport.height, viewport.cameraX, viewport.cameraY, viewport.cameraZ, viewport.projection);
        if (FRAME_RECEIPTS && frameReceipts < 64 && (frameReceipts < 8 || frameState.historyReset)) {
            write(receipt + "-frame-" + frameReceipts++ + ".txt", "dimension=" + dimension + "\nframe=" + viewport.frameId
                    + "\nsize=" + viewport.width + "x" + viewport.height + "\ncamera=" + viewport.cameraX + "," + viewport.cameraY + "," + viewport.cameraZ
                    + "\nhistoryReset=" + frameState.historyReset + "\nhistoryResetReason=" + frameState.resetReason + "\nnear=" + frameState.projection.perspectiveNear()
                    + "\nfar=" + frameState.projection.perspectiveFar() + "\nrenderDistanceBlocks=" + Math.round(me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance * 512)
                    + "\nprojection=" + frameState.projection + "\ninverse=" + frameState.inverseProjection + "\nprevious=" + frameState.previousProjection);
        }
    }

    interface Program {
        int id();
        void bind(Viewport<?> viewport, DhFrameState state);
        String resourceBindings();
        void close();
    }

    @FunctionalInterface
    interface ProgramFactory {
        Program create(ProgramSource source, Stages stages, boolean water, boolean shadow);
    }

    public boolean ready() { return !closed && activationFailure == null && opaqueProgram != null; }

    public void preparePrograms() {
        preparePrograms((source, stages, water, shadow) -> {
            me.cortex.voxy.client.core.gl.shader.Shader shader;
            try {
                shader = me.cortex.voxy.client.core.gl.shader.Shader.make()
                        .addSource(me.cortex.voxy.client.core.gl.shader.ShaderType.VERTEX, stages.vertex())
                        .addSource(me.cortex.voxy.client.core.gl.shader.ShaderType.FRAGMENT, stages.fragment()).compile();
            } catch (RuntimeException failure) {
                throw failure(DhTranslationFailure.Code.COMPILE_OR_LINK_FAILED, source.getName(), failure);
            }
            try { return new DhPrototypeProgram(shader, source, pipeline, uniforms, water, shadow); }
            catch (RuntimeException failure) {
                shader.free();
                throw failure(DhTranslationFailure.Code.BINDING_FAILED, source.getName(), failure);
            }
        }, () -> {
            if (shadowEnabled) {
                shadowFramebuffer = pipeline.createDHFramebufferShadow(shadow);
                int status = shadowFramebuffer.getStatus();
                if (status != org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE)
                    throw failure(DhTranslationFailure.Code.TARGET_BINDING_FAILED, shadow.getName(),
                            new IllegalStateException("shadow framebuffer status=" + status));
            }
        });
    }

    void preparePrograms(ProgramFactory factory, Runnable validateTargets) {
        if (closed) throw failure(DhTranslationFailure.Code.OWNER_DESTROYED, "adapter", null);
        if (activationFailure != null) throw activationFailure;
        if (ready()) return;
        Program opaque = null, water = null, shadow = null;
        long start = System.nanoTime();
        try {
            opaque = factory.create(terrain, stages(false), false, false);
            water = factory.create(this.water, stages(true), true, false);
            if (shadowEnabled) shadow = factory.create(this.shadow, shadowStages(), false, true);
            validateTargets.run();
            write(receipt + "-link.txt", "terrain=" + opaque.id() + " water=" + water.id() + " shadow="
                    + (shadow == null ? 0 : shadow.id()) + " shadowEnabled=" + shadowEnabled
                    + " validated before Voxy renderer activation; owner=Iris pipeline\ncompileNanos=" + (System.nanoTime() - start) + "\n");
        } catch (RuntimeException failure) {
            if (shadow != null) shadow.close();
            if (water != null) water.close();
            if (opaque != null) opaque.close();
            activationFailure = failure instanceof DhTranslationFailure typed ? typed
                    : failure(DhTranslationFailure.Code.TARGET_BINDING_FAILED, "adapter", failure);
            throw activationFailure;
        }
        opaqueProgram = opaque;
        waterProgram = water;
        shadowProgram = shadow;
    }

    public DhTranslationFailure failure(DhTranslationFailure.Code code, String program, Throwable cause) {
        return new DhTranslationFailure(code, environment.pack(), dimension, program,
                cause == null ? "Iris owner is no longer live; reload required" : cause.getMessage(), cause);
    }

    public void invalidateOwnership() {
        if (activationFailure != null) return;
        activationFailure = failure(DhTranslationFailure.Code.LOD_ENGINE_CONFLICT, "adapter",
                new IllegalStateException("DH rendering became active; reload after selecting one LOD renderer"));
        me.cortex.voxy.common.Logger.error(activationFailure);
        environment.translations().clear();
        releasePrograms();
        frameState.reset();
    }

    public void bind(boolean translucent, Viewport<?> viewport) {
        if (!ready()) throw activationFailure == null ? failure(DhTranslationFailure.Code.OWNER_DESTROYED, "adapter", null) : activationFailure;
        (me.cortex.voxy.client.core.util.IrisUtil.irisShadowActive() ? shadowProgram : translucent ? waterProgram : opaqueProgram).bind(viewport, frameState);
    }

    public net.irisshaders.iris.gl.framebuffer.GlFramebuffer shadowFramebuffer() { return shadowFramebuffer; }

    public void recordShadowCounts(me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICViewport viewport) {
        if (output == null || shadowCountReceipts >= 32) return;
        int sampleFrame = shadowCountFrames++;
        if (sampleFrame >= 8 && sampleFrame % 120 != 0) return;
        Path file = output.resolve(receipt + "-shadow-counts-" + shadowCountReceipts++ + ".txt");
        String metadata = "dimension=" + dimension + "\nframe=" + viewport.frameId
                + "\nsampleFrame=" + sampleFrame + "\nsize=" + viewport.width + "x" + viewport.height
                + "\ncamera=" + viewport.cameraX + "," + viewport.cameraY + "," + viewport.cameraZ
                + "\nprojection=" + viewport.projection + "\nmodelView=" + viewport.modelView
                + "\nlightDirection=" + viewport.modelView.m02() + "," + viewport.modelView.m12() + "," + viewport.modelView.m22()
                + "\nshadowPerspective=" + (viewport.projection.m33() == 0)
                + "\nsource=after shadow traversal, raster visibility, command generation and translucent sorting; before MDI"
                + "\nreadback=existing asynchronous DownloadStream; counts are not surviving-fragment proof\n";
        long[] sections = new long[1];
        var downloads = me.cortex.voxy.client.core.rendering.util.DownloadStream.INSTANCE;
        downloads.download(viewport.getRenderList(), 0, 4, (ptr, size) ->
                sections[0] = Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr)));
        // DownloadStream delivers these queued copies in order, after its existing fence signals.
        downloads.download(viewport.drawCountCallBuffer, 0, 24, (ptr, size) -> {
            String counts = "traversalSections=" + sections[0]
                    + "\ncmdGenDispatch=" + Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr)) + ","
                    + Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 4)) + ","
                    + Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 8))
                    + "\nopaqueCommands=" + Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 12))
                    + "\nwaterCommands=" + Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 16))
                    + "\ntemporalCommands=" + Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 20)) + "\n";
            try { Files.createDirectories(file.getParent()); Files.writeString(file, metadata + counts); }
            catch (IOException e) { throw new IllegalStateException("VOXY-005 shadow count receipt write failed", e); }
        });
        owner.recordDhShadowTraversal(this, viewport, shadowCountReceipts - 1);
    }

    public void recordShadowTraversal(Viewport<?> viewport, int sample, int topNodes, int iterations,
            me.cortex.voxy.client.core.gl.GlBuffer queues, me.cortex.voxy.client.core.gl.GlBuffer scene) {
        Path file = output.resolve(receipt + "-shadow-traversal-" + sample + ".txt");
        String metadata = "dimension=" + dimension + "\nframe=" + viewport.frameId + "\ncpuTopNodeCount=" + topNodes
                + "\niterations=" + iterations + "\nsource=after shadow traversal and command generation; before MDI/next main upload\n";
        String[] queueSnapshot = new String[1];
        var downloads = me.cortex.voxy.client.core.rendering.util.DownloadStream.INSTANCE;
        downloads.download(queues, 0, iterations * 16L, (ptr, size) -> {
            var text = new StringBuilder(metadata);
            for (int i = 0; i < iterations; i++) {
                text.append("queue[").append(i).append("]=");
                for (int j = 0; j < 4; j++) {
                    if (j != 0) text.append(',');
                    text.append(Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + i * 16L + j * 4L)));
                }
                text.append('\n');
            }
            queueSnapshot[0] = text.toString();
        });
        downloads.download(scene, 0, 208, (ptr, size) -> {
            var text = new StringBuilder(queueSnapshot[0]).append("sceneMvpColumnMajor=");
            for (int i = 0; i < 16; i++) {
                if (i != 0) text.append(',');
                text.append(org.lwjgl.system.MemoryUtil.memGetFloat(ptr + i * 4L));
            }
            text.append("\nsceneCameraSection=").append(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 64)).append(',')
                    .append(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 68)).append(',').append(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 72));
            text.append("\nscenePackedHizSize=").append(Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 76)));
            text.append("\nsceneCameraSubSection=").append(org.lwjgl.system.MemoryUtil.memGetFloat(ptr + 80)).append(',')
                    .append(org.lwjgl.system.MemoryUtil.memGetFloat(ptr + 84)).append(',').append(org.lwjgl.system.MemoryUtil.memGetFloat(ptr + 88));
            text.append("\nsceneMinScreenArea=").append(org.lwjgl.system.MemoryUtil.memGetFloat(ptr + 92)).append('\n');
            for (int i = 0; i < 6; i++) {
                text.append("sceneFrustum[").append(i).append("]=");
                for (int j = 0; j < 4; j++) {
                    if (j != 0) text.append(',');
                    text.append(org.lwjgl.system.MemoryUtil.memGetFloat(ptr + 96 + i * 16L + j * 4L));
                }
                text.append('\n');
            }
            text.append("sceneRenderQueueMax=").append(Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 192)))
                    .append("\nsceneVisibilityId=").append(Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 196)))
                    .append("\nsceneRequestQueueSize=").append(Integer.toUnsignedLong(org.lwjgl.system.MemoryUtil.memGetInt(ptr + 200)))
                    .append("\nsceneRenderDistanceSquared=").append(org.lwjgl.system.MemoryUtil.memGetFloat(ptr + 204)).append('\n');
            try { Files.createDirectories(file.getParent()); Files.writeString(file, text); }
            catch (IOException e) { throw new IllegalStateException("VOXY-005 shadow traversal receipt write failed", e); }
        });
    }

    public void recordSubmission(boolean translucent, Viewport<?> viewport) {
        if (me.cortex.voxy.client.core.util.IrisUtil.irisShadowActive()) {
            if (translucent ? !shadowWaterSubmitted : !shadowSubmitted) {
                if (translucent) shadowWaterSubmitted = true; else shadowSubmitted = true;
                write(receipt + (translucent ? "-shadow-water-submission.txt" : "-shadow-submission.txt"), "irisPhase=" + pipeline.getPhase() + "\nirisShadowActive=true\nprojection=" + viewport.projection + "\nmodelView=" + viewport.modelView + "\nsize=" + viewport.width + "x" + viewport.height + "\nplacement=" + (translucent ? "after Iris shadow opaque depthcopy; before normal shadow translucent" : "before normal shadow terrain") + "; GPU traversal/cull/MDI\n" + shadowProgram.resourceBindings()
                        + shadowTargetInventory() + "shadowViewportBuffersBytes=" + shadowViewportBytes(viewport) + "\n"
                        + "shadowBoundingDepthBytes=" + (4L * viewport.width * viewport.height) + "\n"
                        + "shadowHiZLogicalBytesUpperBound=" + (16L * Integer.highestOneBit(viewport.width) * Integer.highestOneBit(viewport.height) / 3) + "\n");
            }
            return;
        }
        int slot = translucent ? 1 : 0;
        if (submitted[slot]) return;
        submitted[slot] = true;
        write(receipt + "-" + (translucent ? "water" : "terrain") + "-submission.txt", "mode=" + MODE
                + "\nframe=" + viewport.frameId + "\nsize=" + viewport.width + "x" + viewport.height
                + "\nopaqueDepth=" + owner.fb.getDepthTex().id + "\nwaterDepth=" + owner.fbTranslucent.getDepthTex().id
                + "\nmainDhDepthLogicalBytes=" + (8L * viewport.width * viewport.height)
                + "\nmandatoryWaterDepthCopyBytesReadPlusWrite=" + (8L * viewport.width * viewport.height)
                + "\nopaqueTargets=" + java.util.Arrays.toString(owner._getData().opaqueDrawTargets)
                + "\nwaterTargets=" + java.util.Arrays.toString(owner._getData().translucentDrawTargets)
                + "\nplacement=terrain before normal SOLID; water before normal TRANSLUCENT, after Iris deferred"
                + "\nirisPhase=" + pipeline.getPhase()
                + "\nsampler/attachment terrain=getFlippedAfterPrepare; water=getFlippedAfterTranslucent"
                + "\nprepareFlips=" + pipeline.getFlippedAfterPrepare() + "\nafterTranslucentFlips=" + pipeline.getFlippedAfterTranslucent()
                + "\nwaterDhDepthMeaning=completed opaque fb for dhDepthTex/0/1; pass-entry opaque snapshot (distinct from written depth)"
                + "\ndeferredIrisDhDepthMeaning=dhDepthTex/0 opaque copy populated before Iris deferred; water not drawn yet"
                + "\nlaterIrisDhDepthMeaning=dhDepthTex/0 final fbTranslucent; dhDepthTex1 opaque fb\n"
                + (translucent ? waterProgram : opaqueProgram).resourceBindings() + targetInventory()
                + "\nprojection=" + viewport.projection + "\nmodelView=" + viewport.modelView
                + "\nsubmitted=glMultiDrawElementsIndirectCountARB; execution/visual/cost acceptance NOT inferred\n");
    }

    private String shadowTargetInventory() {
        var targets = ((IrisRenderingPipelineAccessor)pipeline).getShadowRenderTargets();
        var inventory = new StringBuilder("irisOwnedShadowDepth=" + targets.getDepthTexture().getTextureId()
                + "\nirisOwnedShadowOpaqueDepth=" + targets.getDepthTextureNoTranslucents().getTextureId() + "\n");
        for (int i = 0; i < targets.getRenderTargetCount(); i++) {
            var target = targets.get(i);
            if (target != null) inventory.append("irisOwnedShadowColor=").append(i).append(" main=").append(target.getMainTexture())
                    .append(" alt=").append(target.getAltTexture()).append(" format=").append(target.getInternalFormat())
                    .append(" size=").append(target.getWidth()).append('x').append(target.getHeight()).append('\n');
        }
        return inventory.toString();
    }

    private static long shadowViewportBytes(Viewport<?> viewport) {
        var mdic = (me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICViewport)viewport;
        return mdic.drawCountCallBuffer.size() + mdic.drawCallBuffer.size() + mdic.positionScratchBuffer.size()
                + mdic.indirectLookupBuffer.size() + mdic.visibilityBuffer.size();
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

    public void detachRenderer() {
        owner = null;
        frameState.reset();
    }

    private void releasePrograms() {
        if (opaqueProgram != null) opaqueProgram.close();
        if (waterProgram != null) waterProgram.close();
        if (shadowProgram != null) shadowProgram.close();
        opaqueProgram = null;
        waterProgram = null;
        shadowProgram = null;
    }

    public void close() {
        if (closed) return;
        closed = true;
        detachRenderer();
        releasePrograms();
        shadowFramebuffer = null;
        pipeline = null;
        uniforms = null;
        terrainStages = null;
        waterStages = null;
        shadowStages = null;
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
