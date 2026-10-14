#include <windows.h>
#include <stdio.h>
int main(int argc, char **argv) {
	SetCurrentDirectoryA(argv[1]);
	HMODULE h = LoadLibraryA("winExt.dll");
	printf("LoadLibrary=%p err=%lu\n", (void *)h, GetLastError());
	if (h) printf("update=%p centerMouse=%p\n", (void *)GetProcAddress(h, "update"), (void *)GetProcAddress(h, "centerMouse"));
	return 0;
}
