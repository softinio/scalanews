{
  description = "Scala News";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs?ref=nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    # Only the systems Mill publishes native binaries for (see millSources).
    flake-utils.lib.eachSystem [
      "aarch64-darwin"
      "aarch64-linux"
      "x86_64-linux"
    ] (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };

        # Pin Mill to the version in .mill-version, ahead of nixpkgs.
        millVersion = "1.1.10";
        millSources = {
          aarch64-darwin = {
            suffix = "native-mac-aarch64";
            hash = "sha256-QRSTbViDa64TrzpVx7hz4PNPHLufW/GKNjT9IU/4OqM=";
          };
          aarch64-linux = {
            suffix = "native-linux-aarch64";
            hash = "sha256-lwCt1bsYbOWIRCjphxDUub7h8onjKifdfArp0Vze3Ro=";
          };
          x86_64-linux = {
            suffix = "native-linux-amd64";
            hash = "sha256-p3mXy754MUvKjbAgyg4eDX9Clgv82F3NO6OkzlsbX7A=";
          };
        };
        mill =
          let
            source =
              millSources.${system} or (throw "Mill ${millVersion} has no native build for ${system}");
          in
          pkgs.mill.overrideAttrs (_: {
            version = millVersion;
            src = pkgs.fetchurl {
              url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-dist-${source.suffix}/${millVersion}/mill-dist-${source.suffix}-${millVersion}.exe";
              inherit (source) hash;
            };
          });
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [ mill ] ++ (with pkgs; [
            graalvm-ce
            metals
            nodejs_22
            scalafmt
            scala-cli
          ]);

          JAVA_HOME = "${pkgs.graalvm-ce}";
          SCALA_NEWS_CONFIG = "config.json";

          shellHook = ''
            echo "Scala News Development Environment"
            echo "===================================="
            echo ""
            echo "Common commands:"
            echo "  mill scalanews.compile          - Compile the project"
            echo "  mill scalanews.tests.testCached  - Run all tests"
            echo "  mill scalanews.reformat          - Format all code"
            echo "  mill scalanews.run               - Run the application"
            echo ""
            echo "See CLAUDE.md for more commands and project documentation"
            echo ""
          '';
        };
      }
    );
}
