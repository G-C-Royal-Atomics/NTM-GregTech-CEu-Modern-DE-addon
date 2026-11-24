#!/usr/bin/env bash
# Downloads the compile dependencies into libs/ (they are not on a public maven under stable names) and checks them.
# CI runs it; locally it is an alternative to copying the jars from an instance (libs/README.md).
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p libs

fetch() { # <file name> <url> <sha256>
    local out="libs/$1"
    if [ ! -f "$out" ] || ! echo "$3  $out" | sha256sum -c --status; then
        echo "downloading $1"
        curl -fsSL --retry 3 -o "$out.part" "$2"
        mv "$out.part" "$out"
    fi
    echo "$3  $out" | sha256sum -c --quiet
}

# GregTech CEu Modern 8.0.0-SNAPSHOT build 149 (2026-11-22, commit 2918b17), from GT's maven
fetch gtceu-1.21.1-8.0.0-SNAPSHOT+20261122-052213-2918b17.jar \
    https://maven.gtceu.com/com/gregtechceu/gtceu/gtceu-1.21.1/8.0.0-SNAPSHOT/gtceu-1.21.1-8.0.0-20261122.052210-149.jar \
    79719001226b3a0c502abf145f2b2b52408a8398cf8bf81e82280299041e6798
# NTM: Decentralized Edition 1.0.0-beta.15, CurseForge file 9721342
fetch ntm-decentralized-neoforge-1.21.1-1.0.0-beta.15.jar \
    https://mediafilez.forgecdn.net/files/9721/342/ntm-decentralized-neoforge-1.21.1-1.0.0-beta.15.jar \
    fb53a5eb6f992cb2386cd80224d2ff224bed60e397add79ac58406c8e81df8f0
