package net.potatom.foodkortniza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test
    fun acceptsHttpsAndNormalizes() {
        val result = UrlValidator.normalize("https://Example.com")
        assertNull(result.error)
        assertEquals("https://example.com/", result.url)
        assertEquals("example.com", result.host)
    }

    @Test
    fun addsSchemeForBareDomain() {
        val result = UrlValidator.normalize("dodopizza.ru/barnaul")
        assertNull(result.error)
        assertEquals("https://dodopizza.ru/barnaul", result.url)
    }

    @Test
    fun rejectsHttpAndDangerousSchemes() {
        assertTrue(UrlValidator.normalize("http://example.com").error != null)
        assertTrue(UrlValidator.normalize("javascript:alert(1)").error != null)
        assertTrue(UrlValidator.normalize("file:///sdcard/x").error != null)
    }

    @Test
    fun rejectsLocalHostsAndPrivateIps() {
        assertTrue(UrlValidator.normalize("https://localhost").error != null)
        assertTrue(UrlValidator.normalize("https://127.0.0.1").error != null)
        assertTrue(UrlValidator.normalize("https://192.168.1.10").error != null)
        assertTrue(UrlValidator.normalize("https://10.0.0.5").error != null)
    }

    @Test
    fun rejectsCredentialsInUrl() {
        assertTrue(UrlValidator.normalize("https://user:pass@example.com").error != null)
    }

    @Test
    fun hostWildcards() {
        assertTrue(hostAllowed(listOf("grilnica.ru", "*.grilnica.ru"), "barnaul.grilnica.ru"))
        assertTrue(hostAllowed(listOf("grilnica.ru", "*.grilnica.ru"), "grilnica.ru"))
        assertFalse(hostAllowed(listOf("burgerkingrus.ru"), "evil.com"))
        assertFalse(hostAllowed(listOf("*.grilnica.ru"), "grilnica.ru.evil.com"))
    }
}
