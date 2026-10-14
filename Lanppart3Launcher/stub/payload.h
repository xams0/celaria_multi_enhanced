// Payload appended to Celaria Lanppart3.exe (written by packer.c, read by lanppart3.c):
//   [compressed files][index][footer]
//   index : u32 count, u32 build id length, build id, then per file:
//           u16 path length, path (UTF-8, '/' separators), u64 offset, u64 stored size, u64 size, u32 method
//   footer: u64 index offset, u32 index size, "LP3PAYL1"
// method 0 = stored, 1 = LZMS (Windows Compression API)
#pragma once
#include <stdint.h>

#define PAYLOAD_MAGIC "LP3PAYL1"
#define PAYLOAD_FOOTER_SIZE 20
#define METHOD_STORED 0
#define METHOD_LZMS 1
