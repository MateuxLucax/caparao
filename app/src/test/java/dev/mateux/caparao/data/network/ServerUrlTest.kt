package dev.mateux.caparao.data.network

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
    fun cleartextOnlyForHttpWithAHost() {
        listOf(
            "http://coffee.example.com", "http://192.168.0.10:8080", "HTTP://host", " http://host/ ",
        ).forEach { assertTrue(it, ServerUrl.isCleartext(it)) }
        listOf(
            "https://coffee.example.com", "not a url", "ftp://example.com", "http://",
        ).forEach { assertFalse(it, ServerUrl.isCleartext(it)) }
    }
}
