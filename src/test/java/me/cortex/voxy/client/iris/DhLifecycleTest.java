package me.cortex.voxy.client.iris;

import me.cortex.voxy.client.core.rendering.Viewport;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DhLifecycleTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory(Path.of("build"), "dh-lifecycle-test-");
        try {
            var field = net.irisshaders.iris.Iris.class.getDeclaredField("irisConfig");
            field.setAccessible(true);
            field.set(null, new net.irisshaders.iris.config.IrisConfig(scratch.resolve("unused-iris.properties")));
            var environment = DhShaderEnvironment.snapshot(scratch, Map.of(), java.util.List.of());
            successfulReuseAndRebind(environment);
            partialCandidateFailures(environment);
            cacheInvalidation(environment);
        } finally {
            try (var paths = Files.walk(scratch)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("DH owned candidate lifecycle: 64 live-owner reuse/destruction/rebind cycles, partial compile/binding/target failure cleanup and cache invalidation passed.");
    }

    private static DhPrototype prototype(DhShaderEnvironment environment) {
        var stages = DhPrototypeFixtures.stages("identity");
        var terrain = new ProgramSource("dh_terrain", stages.vertex(), null, null, null, stages.fragment(), null, null, null);
        var water = new ProgramSource("dh_water", stages.vertex(), null, null, null, stages.fragment(), null, null, null);
        var shadow = new ProgramSource("dh_shadow", stages.vertex(), null, null, null, stages.fragment(), null, null, null);
        var prototype = new DhPrototype(terrain, water, shadow, environment, "/world0");
        prototype.prepareStages(Map.of());
        return prototype;
    }

    private static void successfulReuseAndRebind(DhShaderEnvironment environment) {
        var prototype = prototype(environment);
        var owned = new HashSet<Integer>();
        int[] allocations = {0};
        DhPrototype.ProgramFactory factory = (source, stages, water, shadow) -> {
            require(!prototype.ready(), "No partial candidate is exposed during construction");
            return new OwnedProgram(++allocations[0], owned);
        };
        for (int cycle = 0; cycle < 64; cycle++) {
            prototype.preparePrograms(factory, () -> require(!prototype.ready(), "Targets validate before activation"));
            var liveGeneration = Set.copyOf(owned);
            for (int rejoin = 0; rejoin < 8; rejoin++) {
                prototype.detachRenderer();
                prototype.preparePrograms(factory, () -> { throw new AssertionError("Live compatible targets cannot allocate again"); });
                require(prototype.ready() && owned.equals(liveGeneration) && owned.size() == 3,
                        "World/renderer rebuild retains exactly the compatible live Iris resource set");
            }
            prototype.close();
            prototype.close();
            require(!prototype.ready() && owned.isEmpty(), "Destroyed Iris owner releases every owned program exactly once");
            try { prototype.preparePrograms(factory, () -> {}); throw new AssertionError("Destroyed owner reactivated"); }
            catch (DhTranslationFailure failure) { require(failure.code() == DhTranslationFailure.Code.OWNER_DESTROYED, "Destroyed owner is explicit"); }
            prototype.bindPipeline(null, null);
            prototype.prepareStages(Map.of());
        }
        require(allocations[0] == 64 * 3, "Renderer rejoin cannot multiply resources within an Iris generation");
    }

    private static void partialCandidateFailures(DhShaderEnvironment environment) {
        for (var code : new DhTranslationFailure.Code[]{DhTranslationFailure.Code.COMPILE_OR_LINK_FAILED, DhTranslationFailure.Code.BINDING_FAILED,
                DhTranslationFailure.Code.TARGET_BINDING_FAILED}) {
            var prototype = prototype(environment);
            var owned = new HashSet<Integer>();
            var cause = new IllegalArgumentException("injected dependency failure");
            var failure = prototype.failure(code, code == DhTranslationFailure.Code.TARGET_BINDING_FAILED ? "dh_shadow" : "dh_water", cause);
            int[] attempts = {0};
            DhPrototype.ProgramFactory factory = (source, stages, water, shadow) -> {
                int count = ++attempts[0];
                if (water && code != DhTranslationFailure.Code.TARGET_BINDING_FAILED) throw failure;
                return new OwnedProgram(count, owned);
            };
            for (int retry = 0; retry < 2; retry++) {
                try {
                    prototype.preparePrograms(factory, () -> { throw failure; });
                    throw new AssertionError("Failed candidate activated");
                } catch (DhTranslationFailure actual) {
                    require(actual == failure && actual.getCause() == cause && actual.pack().equals(environment.pack())
                            && actual.dimension().equals("/world0"), "Exact typed origin and cause survive retry");
                }
                require(!prototype.ready() && owned.isEmpty(), "Rejected candidate releases all prior constructed resources");
            }
            require(attempts[0] == (code == DhTranslationFailure.Code.TARGET_BINDING_FAILED ? 3 : 2),
                    "Failure reuse cannot rebuild or leak the candidate on retry");
            prototype.close();
        }
    }

    private static void cacheInvalidation(DhShaderEnvironment environment) {
        var prototype = prototype(environment);
        var owned = new HashSet<Integer>();
        int[] ids = {0};
        prototype.preparePrograms((source, stages, water, shadow) -> new OwnedProgram(++ids[0], owned), () -> {});
        prototype.invalidateOwnership();
        prototype.invalidateOwnership();
        require(!prototype.ready() && owned.isEmpty(), "DH ownership change releases the adapter and cannot silently resume after conflict");
        var stages = DhPrototypeFixtures.stages("identity");
        var key = new DhTranslationCache.Key(environment.identity(), "/world0", "dh_terrain",
                new DhTranslationCache.Source(stages.vertex(), null, null, null, stages.fragment()), Map.of());
        int[] translations = {0};
        environment.translations().get(key, () -> { translations[0]++; return new DhPrototypeTransform.Stages(stages.vertex(), stages.fragment()); });
        environment.translations().get(key, () -> { throw new AssertionError("Compatible source should remain cached"); });
        environment.translations().clear();
        environment.translations().get(key, () -> { translations[0]++; return new DhPrototypeTransform.Stages(stages.vertex(), stages.fragment()); });
        require(translations[0] == 2, "Invalidation discards the previous derived artifact");
        prototype.close();
    }

    private static final class OwnedProgram implements DhPrototype.Program {
        private final int id;
        private final Set<Integer> owned;
        OwnedProgram(int id, Set<Integer> owned) { this.id = id; this.owned = owned; require(owned.add(id), "Program IDs are unique"); }
        public int id() { return id; }
        public void bind(Viewport<?> viewport, DhFrameState state) { require(owned.contains(id), "No binding to a destroyed program"); }
        public String resourceBindings() { return ""; }
        public void close() { require(owned.remove(id), "Program ownership releases once"); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
