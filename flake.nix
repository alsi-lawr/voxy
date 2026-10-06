{
  description = "Isolated Voxy reference and target development tooling";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/aa48d347080940b8a2b8d2f48228674e280a3514";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      shell = jdk: pkgs.mkShell {
        packages = [ jdk pkgs.git pkgs.pciutils ];
        JAVA_HOME = "${jdk}";
        LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
          pkgs.stdenv.cc.cc.lib
          pkgs.libGL
          pkgs.openal
          pkgs.flite
          pkgs.libx11
          pkgs.libxcursor
          pkgs.libxi
          pkgs.libxrandr
          pkgs.libxxf86vm
        ];
      };
    in {
      devShells.${system} = {
        default = shell pkgs.jdk25;
        upstream = shell pkgs.jdk25;
        target = shell pkgs.jdk17;
      };
    };
}
