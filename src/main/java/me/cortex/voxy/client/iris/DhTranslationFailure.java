package me.cortex.voxy.client.iris;

public final class DhTranslationFailure extends IllegalStateException {
    public enum Code {
        MISSING_PROGRAM, UNSUPPORTED_STAGE, RESERVED_IDENTIFIER, UNSUPPORTED_VERTEX_INPUT,
        UNSUPPORTED_RESOURCE_BINDING, TRANSFORM_FAILED, SHADOW_PASS_UNAVAILABLE, LOD_ENGINE_CONFLICT
    }

    private final Code code;
    private final String pack;
    private final String dimension;
    private final String program;

    DhTranslationFailure(Code code, String pack, String dimension, String program, String detail, Throwable cause) {
        super("VOXY_DH_" + code + " pack=" + pack + " dimension=" + dimension + " program=" + program + ": " + detail, cause);
        this.code = code;
        this.pack = pack;
        this.dimension = dimension;
        this.program = program;
    }

    public Code code() { return code; }
    public String pack() { return pack; }
    public String dimension() { return dimension; }
    public String program() { return program; }
}
