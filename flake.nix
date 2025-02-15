{
  inputs = {
    typelevel-nix.url = "github:typelevel/typelevel-nix";
    nixpkgs.follows = "typelevel-nix/nixpkgs";
    flake-utils.follows = "typelevel-nix/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      typelevel-nix,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ typelevel-nix.overlays.default ];
        };
      in
      {
        devShell = pkgs.devshell.mkShell {
          imports = [ typelevel-nix.typelevelShell ];
          name = "scalanews-shell";
          typelevelShell = {
            jdk.package = pkgs.graalvm-ce;
            nodejs.enable = true;
          };
          commands = [
            {
              name = "ni";
              category = "development";
              help = "Create new scalanews executable";
              command = ''
                sbt coreJVM/nativeImage
                chmod +x target/scalanews
                ./target/scalanews --help
              '';
            }
          ];
        };
      }
    );
}
