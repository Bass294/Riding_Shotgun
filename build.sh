#!/usr/bin/env bash
# Compiles data/scripts into jars/RidingShotgun.jar against the game and library jars.
set -e
cd "$(dirname "$0")"
JDK="${JDK:-$HOME/.jdks/ms-17.0.18}"
CORE="../../starsector-core"
CP="$CORE/starfarer.api.jar;$CORE/starfarer_obf.jar;$CORE/fs.common_obf.jar;$CORE/json.jar;$CORE/log4j-1.2.9.jar;$CORE/lwjgl.jar;$CORE/lwjgl_util.jar;$CORE/xstream-1.4.10.jar"
for j in "../! PatchLib !-0.6.0/jars/PatchLibAPI.jar" "../LazyLib-3.0.0/jars/LazyLib.jar" "../MagicLib-1.5.8rc2/jars/MagicLib.jar" "../LunaLib-2.0.5/jars/LunaLib.jar" "../zz GraphicsLib-1.12.1/jars/Graphics.jar" "../Console Commands-4.0.9/jars/lw_Console.jar"; do CP="$CP;$j"; done
rm -rf out && mkdir -p out jars
find data/scripts -name '*.java' > out/sources.txt
"$JDK/bin/javac" --release 17 -encoding UTF-8 -sourcepath . -cp "$CP" -d out @out/sources.txt
"$JDK/bin/jar" cf jars/RidingShotgun.jar -C out data
echo "Built jars/RidingShotgun.jar"
