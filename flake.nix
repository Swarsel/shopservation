{
  description = "shopservation - Android companion for shopservatory: sounds a loud alarm on matching finds";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    nixpkgs-fdroid.url = "github:NixOS/nixpkgs/nixos-25.05";
  };

  outputs =
    inputs:
    inputs.flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      perSystem =
        { system, ... }:
        let
          pkgs = import inputs.nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          fdroidserver = inputs.nixpkgs-fdroid.legacyPackages.${system}.fdroidserver;
          android = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "34" ];
            buildToolsVersions = [ "34.0.0" ];
            includeEmulator = false;
            includeSystemImages = false;
            includeNDK = false;
          };
        in
        {
          apps = {
            init-keys = {
              type = "app";
              program = "${pkgs.writeShellScript "init-keys" ''
                export PATH=${
                  pkgs.lib.makeBinPath [
                    pkgs.jdk17
                    fdroidserver
                    pkgs.python3
                    android.androidsdk
                  ]
                }:$PATH
                export ANDROID_HOME=${android.androidsdk}/libexec/android-sdk
                export ANDROID_SDK_ROOT=$ANDROID_HOME
                export JAVA_HOME=${pkgs.jdk17.home}
                export SHOPSERVATION_LIB=${./scripts}
                exec ${./scripts/init-keys.sh} "$@"
              ''}";
            };
            publish = {
              type = "app";
              program = "${pkgs.writeShellScript "publish" ''
                export PATH=${
                  pkgs.lib.makeBinPath [
                    pkgs.jdk17
                    pkgs.gradle
                    fdroidserver
                    pkgs.apksigner
                    pkgs.rsync
                    pkgs.openssh
                    android.androidsdk
                  ]
                }:$PATH
                export ANDROID_HOME=${android.androidsdk}/libexec/android-sdk
                export ANDROID_SDK_ROOT=$ANDROID_HOME
                export JAVA_HOME=${pkgs.jdk17.home}
                export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/34.0.0/aapt2"
                export SHOPSERVATION_LIB=${./scripts}
                exec ${./scripts/publish.sh} "$@"
              ''}";
            };
          };

          devShells.default = pkgs.mkShell {
            packages = [
              pkgs.jdk17
              pkgs.gradle
              android.androidsdk
              fdroidserver
              pkgs.apksigner
            ];

            ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
            JAVA_HOME = pkgs.jdk17.home;

            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${android.androidsdk}/libexec/android-sdk/build-tools/34.0.0/aapt2";

            shellHook = ''
              {
                echo "shopservation dev shell"
                echo "  gradle test               run unit tests"
                echo "  gradle assembleDebug      build a debug APK for adb install"
                echo "  nix run .#init-keys       create signing keys (once, then back them up)"
                echo "  nix run .#publish         build signed APK + refresh the F-Droid repo"
              } >&2
            '';
          };
        };
    };
}
