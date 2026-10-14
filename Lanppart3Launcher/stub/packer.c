// Build tool: appends a folder to the launcher stub.
// usage: packer <stub.exe> <folder> <build id> <output.exe>
// Every file of the folder is compressed (LZMS) and appended with an index (payload.h).
#include <windows.h>
#include <compressapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "payload.h"

typedef struct {
	char path[512];
	uint64_t offset, stored, size;
	uint32_t method;
} Entry;

static Entry *entries;
static int count, capacity;
static FILE *out;
static COMPRESSOR_HANDLE compressor;

static void fail(const char *what) {
	fprintf(stderr, "packer: %s (error %lu)\n", what, GetLastError());
	exit(1);
}

static void add_file(const wchar_t *full, const char *relative) {
	HANDLE f = CreateFileW(full, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
	if (f == INVALID_HANDLE_VALUE) fail("open");
	LARGE_INTEGER size;
	GetFileSizeEx(f, &size);
	uint8_t *data = malloc(size.QuadPart + 1);
	DWORD read = 0;
	if (size.QuadPart > 0 && (!ReadFile(f, data, (DWORD)size.QuadPart, &read, NULL) || read != size.QuadPart)) fail("read");
	CloseHandle(f);
	if (count == capacity) {
		capacity = capacity ? capacity * 2 : 256;
		entries = realloc(entries, capacity * sizeof *entries);
	}
	Entry *e = &entries[count++];
	snprintf(e->path, sizeof e->path, "%s", relative);
	e->offset = _ftelli64(out);
	e->size = size.QuadPart;
	SIZE_T bound = (SIZE_T)size.QuadPart + (SIZE_T)size.QuadPart / 8 + 65536, written = 0;
	uint8_t *packed = malloc(bound);
	if (size.QuadPart >= 256 && Compress(compressor, data, (SIZE_T)size.QuadPart, packed, bound, &written) && written < size.QuadPart) {
		e->method = METHOD_LZMS;
		e->stored = written;
		fwrite(packed, 1, written, out);
	} else {
		e->method = METHOD_STORED;
		e->stored = size.QuadPart;
		fwrite(data, 1, (size_t)size.QuadPart, out);
	}
	free(packed);
	free(data);
}

static void add_folder(const wchar_t *folder, const char *prefix) {
	wchar_t pattern[1024];
	_snwprintf(pattern, 1024, L"%ls\\*", folder);
	WIN32_FIND_DATAW fd;
	HANDLE h = FindFirstFileW(pattern, &fd);
	if (h == INVALID_HANDLE_VALUE) return;
	do {
		if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) continue;
		wchar_t full[1024];
		_snwprintf(full, 1024, L"%ls\\%ls", folder, fd.cFileName);
		char name[512], relative[1024];
		WideCharToMultiByte(CP_UTF8, 0, fd.cFileName, -1, name, sizeof name, NULL, NULL);
		snprintf(relative, sizeof relative, "%s%s%s", prefix, prefix[0] ? "/" : "", name);
		if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) add_folder(full, relative);
		else add_file(full, relative);
	} while (FindNextFileW(h, &fd));
	FindClose(h);
}

static void write_u16(uint16_t v) { fwrite(&v, 2, 1, out); }
static void write_u32(uint32_t v) { fwrite(&v, 4, 1, out); }
static void write_u64(uint64_t v) { fwrite(&v, 8, 1, out); }

int wmain(int argc, wchar_t **argv) {
	if (argc != 5) {
		fprintf(stderr, "usage: packer <stub.exe> <folder> <build id> <output.exe>\n");
		return 1;
	}
	if (!CopyFileW(argv[1], argv[4], FALSE)) fail("copy stub");
	out = _wfopen(argv[4], L"ab");
	if (out == NULL) fail("open output");
	_fseeki64(out, 0, SEEK_END);
	if (!CreateCompressor(COMPRESS_ALGORITHM_LZMS, NULL, &compressor)) fail("compressor");
	add_folder(argv[2], "");
	char build[128];
	WideCharToMultiByte(CP_UTF8, 0, argv[3], -1, build, sizeof build, NULL, NULL);
	uint64_t index = _ftelli64(out);
	write_u32(count);
	write_u32((uint32_t)strlen(build));
	fwrite(build, 1, strlen(build), out);
	for (int i = 0; i < count; i++) {
		write_u16((uint16_t)strlen(entries[i].path));
		fwrite(entries[i].path, 1, strlen(entries[i].path), out);
		write_u64(entries[i].offset);
		write_u64(entries[i].stored);
		write_u64(entries[i].size);
		write_u32(entries[i].method);
	}
	uint64_t end = _ftelli64(out);
	write_u64(index);
	write_u32((uint32_t)(end - index));
	fwrite(PAYLOAD_MAGIC, 1, 8, out);
	fclose(out);
	uint64_t total = 0, stored = 0;
	for (int i = 0; i < count; i++) {
		total += entries[i].size;
		stored += entries[i].stored;
	}
	printf("packer: %d files, %.1f MB -> %.1f MB\n", count, total / 1048576.0, stored / 1048576.0);
	return 0;
}
