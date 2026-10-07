package me.cortex.voxy.client.core.gl.shader;

import java.util.HashSet;
import java.util.Set;

public final class ShaderCompilationTest {
    public static void main(String[] args) {
        for (int cycle = 0; cycle < 64; cycle++) {
            var builder = Shader.make().addSource(ShaderType.VERTEX, "#version 330\nvoid main() {}")
                    .addSource(ShaderType.FRAGMENT, "#version 330\nvoid main() {}");
            for (int failureAt : new int[]{1, 2, 3, 0}) {
                var compiler = new OwnedCompiler(failureAt);
                try {
                    int program = builder.compileToProgram(compiler);
                    require(failureAt == 0 && compiler.programs.equals(Set.of(program)) && compiler.shaders.isEmpty(),
                            "Only a successfully linked executable transfers to the caller; no attached stages survive");
                    compiler.deleteProgram(program);
                } catch (IllegalStateException failure) {
                    require(failure == compiler.failure && failureAt != 0, "The original stage/link cause must propagate");
                }
                require(compiler.programs.isEmpty() && compiler.shaders.isEmpty() && compiler.attachments.isEmpty(),
                        "Partial compilation and linking cannot retain GL resources across reload attempts");
            }
        }
        System.out.println("Shader compilation ownership: 64 repeated stage/link failure and successful transfer cycles passed.");
    }

    private static final class OwnedCompiler implements Shader.Builder.Compiler {
        final IllegalStateException failure = new IllegalStateException("driver failure");
        final Set<Integer> programs = new HashSet<>(), shaders = new HashSet<>(), attachments = new HashSet<>();
        final int failureAt;
        int step;
        int next = 1;
        OwnedCompiler(int failureAt) { this.failureAt = failureAt; }
        public int createProgram() { int id = next++; programs.add(id); return id; }
        public int createShader(ShaderType type, String source) {
            if (++step == failureAt) throw failure;
            int id = next++; shaders.add(id); return id;
        }
        public void attach(int program, int shader) {
            require(programs.contains(program) && shaders.contains(shader), "Attachment requires live objects");
            attachments.add(shader);
        }
        public void link(int program) { if (++step == failureAt) throw failure; }
        public void detach(int program, int shader) {
            require(programs.contains(program) && attachments.remove(shader), "Stages detach exactly once from a live program");
        }
        public void deleteShader(int shader) {
            require(!attachments.contains(shader) && shaders.remove(shader), "Each allocated stage is released once, with no retained attachment");
        }
        public void deleteProgram(int program) {
            require(programs.remove(program), "Each allocated program is released once");
            attachments.clear();
        }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
