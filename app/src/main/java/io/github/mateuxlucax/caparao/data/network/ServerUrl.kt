package io.github.mateuxlucax.caparao.data.network

import java.net.InetAddress
import java.net.URI

/** Checks on the server URL the user types. */
object ServerUrl {

    /** Only http(s) URLs with a host can be uploaded to. */
    fun isValid(url: String): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
    }

    /** True for plain http to a host that isn't on the local network, where the token travels unencrypted. */
    fun isCleartextToPublicHost(url: String): Boolean {
        if (parse(url)?.scheme?.lowercase() != "http") return false
        val host = host(url) ?: return false
        return !isLocal(host)
    }

    private fun parse(url: String): URI? = try {
        URI(url.trim())
    } catch (e: Exception) {
        null
    }

    /** Lowercase host of [url] without IPv6 brackets, or null when it has none. */
    fun host(url: String): String? = parse(url)?.host?.lowercase()?.removeSurrounding("[", "]")?.ifEmpty { null }

    private fun isLocal(host: String): Boolean {
        if (host == "localhost" || LOCAL_SUFFIXES.any { host.endsWith(it) }) return true
        // Only IP literals are checked: resolving a name here would mean a DNS lookup on the UI thread.
        val isIpLiteral = host.matches(IPV4) || ':' in host
        if (!isIpLiteral) return false
        val address = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return false
        }
        return isLocalAddress(address)
    }

    /**
     * Local address: private, loopback, link-local, unique local or CGNAT. 10/8, 172.16/12, 192.168/16,
     * 127/8, 169.254/16, 100.64/10 (shared address space, which Android counts as local and Tailscale
     * hands out), ::1, fc00::/7 (which includes Tailscale's fd7a:115c:a1e0::/48) and fe80::/10.
     */
    fun isLocalAddress(address: InetAddress): Boolean {
        val bytes = address.address.map { it.toInt() and 0xFF }
        return if (bytes.size == 4) {
            val (a, b) = bytes
            a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        } else {
            val loopback = bytes.dropLast(1).all { it == 0 } && bytes.last() == 1
            val uniqueLocal = (bytes[0] and 0xFE) == 0xFC // fc00::/7
            val linkLocal = bytes[0] == 0xFE && (bytes[1] and 0xC0) == 0x80 // fe80::/10
            loopback || uniqueLocal || linkLocal
        }
    }

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa")
}
