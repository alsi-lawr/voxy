package me.cortex.voxy.client.iris;

import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import net.irisshaders.iris.helpers.StringPair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DhShaderEnvironment {
    private final String pack;
    private final String identity;
    private final DhTranslationCache translations = new DhTranslationCache();

    private DhShaderEnvironment(String pack, String identity) {
        this.pack = pack;
        this.identity = identity;
    }

    static DhShaderEnvironment snapshot(Path root, Map<String, String> options, List<StringPair> defines) throws IOException {
        var sourceHashes = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                sourceHashes.put(root.relativize(path).toString(), DhPrototype.hash(Files.readAllBytes(path)));
            }
        }
        return new DhShaderEnvironment(root.toUri().toString(), identity(sourceHashes, options, defines, contract()));
    }

    static String identity(Map<String, String> sourceHashes, Map<String, String> options, List<StringPair> defines, String contract) {
        var fields = new java.util.ArrayList<String>();
        fields.add(contract);
        fields.add(Integer.toString(sourceHashes.size()));
        new TreeMap<>(sourceHashes).forEach((name, hash) -> { fields.add(name); fields.add(hash); });
        fields.add("options");
        fields.add(Integer.toString(options.size()));
        new TreeMap<>(options).forEach((name, value) -> { fields.add(name); fields.add(value); });
        fields.add("defines");
        fields.add(Integer.toString(defines.size()));
        defines.forEach(pair -> { fields.add(pair.key()); fields.add(pair.value()); });
        return fingerprint(fields);
    }

    static String fingerprint(List<String> fields) {
        var text = new StringBuilder();
        fields.forEach(field -> text.append(field.length()).append(':').append(field));
        return DhPrototype.hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String contract() {
        return "voxy-dh-input-v1/iris-1.7.6+1.20.1/glsl-transformer-2.0.1/jcpp-1.4.14/" +
                implementationHash(DhPrototypeTransform.class) + "/" + implementationHash(DhPrototypeInputs.class) + "/" +
                implementationHash(net.irisshaders.iris.pipeline.transform.TransformPatcher.class) + "/" +
                DhPrototype.hash(ShaderLoader.parse("voxy:lod/gl46/dh_prototype_vertex.glsl").getBytes(StandardCharsets.UTF_8));
    }

    private static String implementationHash(Class<?> implementation) {
        try (var bytes = implementation.getResourceAsStream(implementation.getSimpleName() + ".class")) {
            return DhPrototype.hash(java.util.Objects.requireNonNull(bytes).readAllBytes());
        } catch (IOException exception) { throw new IllegalStateException("Cannot fingerprint loaded DH translation implementation", exception); }
    }

    public String pack() { return pack; }
    public String identity() { return identity; }
    DhTranslationCache translations() { return translations; }
}
