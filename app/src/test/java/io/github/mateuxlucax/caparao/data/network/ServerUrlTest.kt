package io.github.mateuxlucax.caparao.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    @Test
    fun validUrls() {
        listOf(
            "http://192.168.0.10:8080", "https://coffee.example.com", "HTTP://host", " http://host/ ",
            "http://[::1]:8080", "https://example.com/prefix",
        ).forEach { assertTrue(it, ServerUrl.isValid(it)) }
    }

    @Test
    fun invalidUrls() {
        listOf(
            "", "192.168.0.10:8080", "ftp://example.com", "file:///etc/passwd", "http://", "http:// spaces",
            "mailto:me@example.com", "javascript:alert(1)", "http//example.com",
        ).forEach { assertFalse(it, ServerUrl.isValid(it)) }
    }

    @Test
    fun cleartextToLocalHostsIsFine() {
        listOf(
            "http://localhost:8080", "http://LOCALHOST", "http://pi.local", "http://nas.lan",
            "http://server.home.arpa",
        ).forEach { assertFalse(it, ServerUrl.isCleartextToPublicHost(it)) }
    }

    @Test
    fun cleartextToPublicHostsWarns() {
        listOf(
            "http://example.com", "http://local.example.com", "http://example.lan.com",
        ).forEach { assertTrue(it, ServerUrl.isCleartextToPublicHost(it)) }
    }

    @Test
    fun cleartextWarnsOnlyForPublicAddresses() {
        LocalAddressCases.all.forEach { (address, local) ->
            val url = LocalAddressCases.url(address)
            assertEquals(url, local, !ServerUrl.isCleartextToPublicHost(url))
        }
    }

    @Test
    fun httpsNeverWarns() {
        assertFalse(ServerUrl.isCleartextToPublicHost("https://example.com"))
        assertFalse(ServerUrl.isCleartextToPublicHost("not a url"))
    }
}
