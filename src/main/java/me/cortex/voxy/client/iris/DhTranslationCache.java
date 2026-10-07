package me.cortex.voxy.client.iris;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class DhTranslationCache {
    record Source(String vertex, String geometry, String tessControl, String tessEval, String fragment) {
        static Source from(ProgramSource source) {
            return new Source(source.getVertexSource().orElse(null), source.getGeometrySource().orElse(null),
                    source.getTessControlSource().orElse(null), source.getTessEvalSource().orElse(null), source.getFragmentSource().orElse(null));
        }
    }
    record Key(String environment, String dimension, String program, Source source,
               Map<Tri<String, TextureType, TextureStage>, String> textures) {
        Key { textures = Map.copyOf(textures); }
    }
    private record Result(DhPrototypeTransform.Stages stages, DhTranslationFailure failure) {
        DhPrototypeTransform.Stages get() {
            if (failure != null) throw failure;
            return stages;
        }
    }
    private final Map<Key, Result> results = new HashMap<>();

    synchronized DhPrototypeTransform.Stages translate(String pack, Key key) {
        return get(key, () -> {
            Source source = key.source();
            if (source.vertex() == null || source.fragment() == null) {
                throw failure(DhTranslationFailure.Code.MISSING_PROGRAM, pack, key, "vertex and fragment stages are required", null);
            }
            if (source.geometry() != null || source.tessControl() != null || source.tessEval() != null) {
                throw failure(DhTranslationFailure.Code.UNSUPPORTED_STAGE, pack, key, "geometry/tessellation cannot use Voxy's packed quad submission", null);
            }
            try {
                return DhPrototypeTransform.transform(key.program(), source.vertex(), source.fragment(), new Object2ObjectOpenHashMap<>(key.textures()));
            } catch (DhPrototypeTransform.Unsupported unsupported) {
                throw failure(unsupported.code, pack, key, unsupported.getMessage(), unsupported);
            } catch (RuntimeException exception) {
                throw failure(DhTranslationFailure.Code.TRANSFORM_FAILED, pack, key, exception.getMessage(), exception);
            }
        });
    }

    synchronized DhPrototypeTransform.Stages get(Key key, Supplier<DhPrototypeTransform.Stages> translate) {
        var result = results.get(key);
        if (result == null) {
            try { result = new Result(translate.get(), null); }
            catch (DhTranslationFailure failure) { result = new Result(null, failure); }
            results.put(key, result);
        }
        return result.get();
    }

    synchronized void clear() { results.clear(); }

    private static DhTranslationFailure failure(DhTranslationFailure.Code code, String pack, Key key, String detail, Throwable cause) {
        return new DhTranslationFailure(code, pack, key.dimension(), key.program(), detail, cause);
    }
}
