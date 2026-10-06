Voxy is an LoD rendering mod for minecraft

## Java requirements

This Minecraft 1.20.1 target requires Java 17 and the incubating Vector API module.
The Java runtime must contain `jdk.incubator.vector` and start with
`--add-modules=jdk.incubator.vector`. Without that JVM argument, the level-0
section decoder cannot load its non-air counter. Launcher profiles are not
configured automatically. The API is incubating; optimization depends on the
JIT and hardware.

Build Gradle/Loom with Java 21, using the Java 17 compiler toolchain and
`--release 17`. The isolated `nix develop .#build` environment provides both.
The compiler, non-air headless test and JMH task resolve the Vector API module;
emitted Voxy classes still target Java 17.
