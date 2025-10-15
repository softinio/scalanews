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
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            graalvm-ce
            metals
            mill
            nodejs_22
            scalafmt
            scala-cli
          ];

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
      });
}
