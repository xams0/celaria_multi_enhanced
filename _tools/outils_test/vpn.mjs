import fs from "node:fs";
const f = "lan.c"; let s = fs.readFileSync(f, "utf8");
const rep = (o, n) => { if (!s.includes(o)) { console.error("missing: " + o); process.exit(1); } s = s.replace(o, () => n); };
rep(`// When the game connects to a server, the TCP connections of this process to the port of the server are read from the
// Windows TCP table. The mod becomes active only if such a connection exists and every one of them goes to a private /
// loopback / link-local address. Otherwise (single player, menus, servers on the Internet, any error) the mod is passive:
// every hook calls the original function, nothing is sent, displayed or blocked.`,
`// When the game connects to a server, the TCP connections of this process to the port of the server are read from the
// Windows TCP table. The mod becomes active only if such a connection exists and every one of them goes to a local
// address: private / loopback / link-local / shared (100.64/10, Tailscale) ranges, or an address inside the subnet of an
// active network adapter of this computer (virtual LANs like Radmin VPN 26.0.0.0/8, Hamachi 25.0.0.0/8, ZeroTier).
// Otherwise (single player, menus, servers on the Internet, any error) the mod is passive: every hook calls the original
// function, nothing is sent, displayed or blocked.`);
rep(`	return a == 127 || a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254);
}`, `	return a == 127 || a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254)
		|| (a == 100 && b >= 64 && b <= 127);
}

// address inside the subnet of an active adapter (prefix of at least 8 bits for IPv4, 16 for IPv6)
static int on_local_subnet(int family, const void *address) {
	ULONG size = 16 * 1024;
	IP_ADAPTER_ADDRESSES *list = NULL;
	ULONG ret = ERROR_BUFFER_OVERFLOW;
	for (int tries = 0; tries < 3 && ret == ERROR_BUFFER_OVERFLOW; tries++) {
		free(list);
		list = malloc(size);
		if (list == NULL) return 0;
		ret = GetAdaptersAddresses(family, GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER, NULL, list, &size);
	}
	int found = 0;
	if (ret == NO_ERROR) {
		for (IP_ADAPTER_ADDRESSES *ad = list; ad && found == 0; ad = ad->Next) {
			if (ad->OperStatus != IfOperStatusUp || ad->IfType == IF_TYPE_SOFTWARE_LOOPBACK) continue;
			for (IP_ADAPTER_UNICAST_ADDRESS *u = ad->FirstUnicastAddress; u && found == 0; u = u->Next) {
				const struct sockaddr *sa = u->Address.lpSockaddr;
				int prefix = u->OnLinkPrefixLength;
				if (sa == NULL || sa->sa_family != family) continue;
				if (family == AF_INET) {
					if (prefix < 8 || prefix > 32) continue;
					uint32_t mask = prefix == 32 ? 0xffffffffu : ~(0xffffffffu >> prefix);
					uint32_t local = ntohl(((const struct sockaddr_in *)sa)->sin_addr.s_addr);
					uint32_t remote = ntohl(*(const DWORD *)address);
					if ((local & mask) == (remote & mask)) {
						found = 1;
						mod_log("server on the subnet of the adapter %ls", ad->FriendlyName);
					}
				} else {
					if (prefix < 16 || prefix > 128) continue;
					const UCHAR *l = ((const struct sockaddr_in6 *)sa)->sin6_addr.s6_addr, *r = address;
					int bytes = prefix / 8, bits = prefix % 8;
					if (memcmp(l, r, bytes) == 0 && (bits == 0 || ((l[bytes] ^ r[bytes]) & (0xff << (8 - bits))) == 0)) {
						found = 1;
						mod_log("server on the subnet of the adapter %ls", ad->FriendlyName);
					}
				}
			}
		}
	}
	free(list);
	return found;
}`);
rep(`			if (private_ipv4(r->dwRemoteAddr) == 0) publicFound = 1;`, `			if (private_ipv4(r->dwRemoteAddr) == 0 && on_local_subnet(AF_INET, &r->dwRemoteAddr) == 0) publicFound = 1;`);
rep(`			if (private_ipv6(r->ucRemoteAddr) == 0) publicFound = 1;`, `			if (private_ipv6(r->ucRemoteAddr) == 0 && on_local_subnet(AF_INET6, r->ucRemoteAddr) == 0) publicFound = 1;`);
rep(`	g_lan_session = port > 0 && port < 65536 && connections_private(port);`, `	g_lan_session = port > 0 && port < 65536 && connections_private(port);
	// the mod_log above (adapter) names the virtual LAN when the server isn't in a private range`);
fs.writeFileSync(f, s); console.log("ok");
