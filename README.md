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

## Build and download packages

This backport is distributed from [alsi-lawr/voxy](https://github.com/alsi-lawr/voxy).
The **Package Minecraft 1.20.1** GitHub Actions workflow runs only when manually
requested or when a GitHub release is explicitly published. Ordinary pushes and
pull requests do not create releases or run build/test jobs.

For a manual build, select the desired ref with **Run workflow**, then download
the package from the completed run's **Artifacts** section (GitHub sign-in is
required). Unzip it before installing. Published releases offer the same JAR,
`SHA256SUMS` and `BUILD-INFO.txt` as individual release assets. The workflow builds
the release's tagged commit; it does not create a release. Existing assets are
not overwritten, so a repeated attachment attempt fails rather than replacing
downloads silently.

Install only `voxy-<version>.jar` in the Minecraft 1.20.1 Fabric profile's `mods`
directory, not a development, sources or JMH JAR. This one remapped package
contains the existing Linux and Windows x86-64 storage natives; that is not a
new Windows gameplay verification claim. Keep the Java 17 Vector API JVM
argument above. `BUILD-INFO.txt` identifies the source commit and workflow run;
verify the downloaded JAR with `sha256sum -c SHA256SUMS` from its directory.
Inherited licence and author attribution remain in the JAR and `LICENSE.md`.

For local packaging only (no tests, checks, benchmarks or Minecraft launch):

```sh
nix develop .#build -c ./gradlew --no-daemon remapJar -x compileTestJava -x compileJmhJava
```

Without Nix, run Gradle on Java 21 and set `JAVA17_HOME` to a Java 17 JDK. The
exclusions prevent Loom's source-set discovery from scheduling test/JMH
compilation; they do not remove the existing source tests or bundled natives.
The installable output is `build/libs/voxy-<mod_version>.jar`, using `mod_version`
from `gradle.properties`; do not distribute every JAR in that directory.
