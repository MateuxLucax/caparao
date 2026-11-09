package io.github.mateuxlucax.caparao.data.network

/**
 * The address classification [ServerUrl.isLocalAddress] defines, used by [LocalNetworkTest] so every local address
 * family the server form accepts is pinned in one table.
 */
object LocalAddressCases {

    /** Every case pairs an address literal with whether it is local. */
    val all: List<Pair<String, Boolean>> = listOf(
        // Private, loopback and link-local.
        "10.0.2.2" to true,
        "172.16.0.1" to true,
        "172.31.255.255" to true,
        "192.168.0.10" to true,
        "127.0.0.1" to true,
        "169.254.1.1" to true,
        // CGNAT, 100.64.0.0/10, the range Tailscale hands out.
        "100.64.0.1" to true,
        "100.127.255.254" to true,
        "100.127.255.255" to true,
        // IPv6 loopback, unique local (including Tailscale's ULA) and link-local.
        "::1" to true,
        "fc00::1" to true,
        "fd12:3456::1" to true,
        "fd7a:115c:a1e0::1" to true,
        "fe80::1" to true,
        "febf::1" to true,
        // Public.
        "8.8.8.8" to false,
        "11.0.0.1" to false,
        "172.15.0.1" to false,
        "172.32.0.1" to false,
        "192.169.0.1" to false,
        "100.63.255.255" to false,
        "100.128.0.0" to false,
        "2001:db8::1" to false,
        "fec0::1" to false,
    )

    /** A URL with [address] as its host, bracketed when it is IPv6. */
    fun url(address: String): String = if (':' in address) "http://[$address]" else "http://$address"
}
