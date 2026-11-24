#!/usr/bin/env bash
# In-game stand (dev/MoldStand): GT molds in NTM's foundry mold / basin / strand caster through real clicks and GUI.
# Runs the client with the owner's other mods (extra-mods/) in a nested kwin (vkwin.sh, never kwin directly) and
# through the shared stand slots (stand-slot.sh: memory threshold, waits while the owner plays).
# Result: run/ntmgt-stand.txt (PASS/FAIL per step, last line RESULT; missing last line = the game crashed),
# screenshots run/screenshots/ntmgt-stand-*.png, crash reports run/crash-reports/.
# The world is a fresh reflink copy of notes/stand-world (made once from run/world, the dev server's world).
set -euo pipefail
cd "$(dirname "$0")/.."
P=$PWD
SLOT=$HOME/ntm-gunfeel-project/stand-slot.sh
VKWIN=$HOME/ntm-gunfeel-project/ntm-gunfeel/notes/ports/vkwin.sh
for d in /proc/[0-9]*; do  # a client already running in this run dir
    [ "$(readlink "$d/cwd" 2>/dev/null)" = "$P/run" ] && { echo "REFUSED: a game is already running in run/" >&2; exit 3; }
done
[ -d notes/stand-world ] || { [ -d run/world ] && cp -a --reflink=auto run/world notes/stand-world; }
rm -rf run/saves/ntmgt-stand run/ntmgt-stand.txt run/screenshots/ntmgt-stand-* run/crash-reports run/exit-threads.txt
mkdir -p run/saves && cp -a --reflink=auto notes/stand-world run/saves/ntmgt-stand && rm -f run/saves/ntmgt-stand/session.lock
cat > notes/stand-run.sh <<EOR
#!/bin/bash
cd "$P"
export JAVA_HOME=$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-delta
env -u WAYLAND_DISPLAY timeout 1200 ./gradlew runMoldStand --console=plain -q < /dev/null > run/stand.log 2>&1
EOR
chmod +x notes/stand-run.sh
"$SLOT" "timeout 1260 $VKWIN ntmgt-stand 1280 720 $P/notes/stand-run.sh > $P/run/vkwin-stand.log 2>&1" || true
rm -rf run/saves/ntmgt-stand
if [ -f run/ntmgt-stand.txt ]; then
    grep -c '^PASS' run/ntmgt-stand.txt | sed 's/^/PASS lines: /'; grep '^FAIL' run/ntmgt-stand.txt || true
    tail -1 run/ntmgt-stand.txt
else
    echo "NO RESULT: the game crashed or hung"; ls run/crash-reports 2>/dev/null
    grep -aE "Exception|Caused by|Reported exception" run/logs/latest.log | head -20
fi
