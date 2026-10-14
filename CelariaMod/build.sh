#!/bin/sh
# Builds the mod with llvm-mingw (i686):
#   out/winExt.dll    proxy of the winExt.dll of the game (install.bat: copied in the folder of the game)
#   out/lanppart3.dll same mod injected by the Celaria Lanppart3 launcher (nothing copied in the folder of the game)
cd "$(dirname "$0")"
CC="../_tools/llvm-mingw-20261006-ucrt-x86_64/bin/i686-w64-mingw32-gcc"
mkdir -p out
"$CC" -O2 -Wall -shared -static -o out/winExt.dll src/*.c src/winExt.def -liphlpapi -lws2_32 -Wl,--enable-stdcall-fixup && echo "built out/winExt.dll" &&
"$CC" -O2 -Wall -shared -static -DMOD_INJECTED -o out/lanppart3.dll src/*.c -liphlpapi -lws2_32 && echo "built out/lanppart3.dll"
