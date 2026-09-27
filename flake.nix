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

        # Everything to run before opening a pull request, in one Mill run.
        # testForked (not testCached) so the tests, some of which fetch live
        # feeds, always run as a final check.
        pre-pr = pkgs.writeShellApplication {
          name = "pre-pr";
          runtimeInputs = [ mill ];
          text = ''
            mill scalanews.compile + scalanews.checkFormat + checkDependencyOrder + scalanews.tests.testForked
          '';
        };

        # Regenerate Mill's BSP connection files for IDEs (Metals, IntelliJ).
        bsp-install = pkgs.writeShellApplication {
          name = "bsp-install";
          runtimeInputs = [ mill ];
          text = ''
            mill mill.bsp.BSP/install
          '';
        };

        # Build the documentation site and serve it at http://localhost:4242.
        # docs.preview runs scripts/LaikaPreview.scala with scala-cli.
        docs-preview = pkgs.writeShellApplication {
          name = "docs-preview";
          runtimeInputs = [
            mill
            pkgs.scala-cli
          ];
          text = ''
            mill docs.preview
          '';
        };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            bsp-install
            docs-preview
            mill
            pre-pr
          ]
          ++ (with pkgs; [
            graalvmPackages.graalvm-ce
            metals
            nodejs_22
            scalafmt
            scala-cli
          ]);

          JAVA_HOME = "${pkgs.graalvmPackages.graalvm-ce}";
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
            echo "  pre-pr                           - Compile, check formatting and dependency order, run tests"
            echo "  bsp-install                      - Set up Mill's BSP connection for your IDE"
            echo "  docs-preview                     - Build the docs site and serve it at http://localhost:4242"
            echo ""
            echo "See CLAUDE.md for more commands and project documentation"
            echo ""
          '';
        };
      }
    );
}
