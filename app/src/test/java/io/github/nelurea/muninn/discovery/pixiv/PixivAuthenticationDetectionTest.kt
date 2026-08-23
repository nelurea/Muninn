package io.github.nelurea.muninn.discovery.pixiv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivAuthenticationDetectionTest {

    @Test
    fun `only unauthorized and forbidden HTTP statuses require authentication`() {
        assertTrue(isPixivAuthenticationStatus(401))
        assertTrue(isPixivAuthenticationStatus(403))
        assertFalse(isPixivAuthenticationStatus(400))
        assertFalse(isPixivAuthenticationStatus(404))
        assertFalse(isPixivAuthenticationStatus(429))
        assertFalse(isPixivAuthenticationStatus(500))
    }

    @Test
    fun `requires a non-empty PHPSESSID cookie`() {
        assertTrue(hasPixivSessionCookie("foo=1; PHPSESSID=session-value; bar=2"))
        assertFalse(hasPixivSessionCookie(null))
        assertFalse(hasPixivSessionCookie("foo=1; bar=2"))
        assertFalse(hasPixivSessionCookie("PHPSESSID="))
        assertFalse(hasPixivSessionCookie("NOT_PHPSESSID=session-value"))
    }

    @Test
    fun `recognizes a Pixiv login final URL`() {
        assertTrue(
            isPixivLoginResponse(
                "https://accounts.pixiv.net/login?return_to=example",
                ""
            )
        )
    }

    @Test
    fun `recognizes explicit Pixiv login HTML`() {
        assertTrue(
            isPixivLoginResponse(
                "https://www.pixiv.net/ajax/search/artworks/cat",
                "<!doctype html><a href=\"https://accounts.pixiv.net/login\">Log in</a>"
            )
        )
    }

    @Test
    fun `does not classify generic HTML or malformed JSON as login`() {
        assertFalse(
            isPixivLoginResponse(
                "https://www.pixiv.net/ajax/search/artworks/cat",
                "<!doctype html><html><title>Temporary error</title></html>"
            )
        )
        assertFalse(
            isPixivLoginResponse(
                "https://www.pixiv.net/ajax/search/artworks/cat",
                "{not-json"
            )
        )
    }
}
