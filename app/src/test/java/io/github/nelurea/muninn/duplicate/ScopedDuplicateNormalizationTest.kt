package io.github.nelurea.muninn.duplicate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedDuplicateNormalizationTest {
    @Test
    fun `strict scope accepts a non-empty unique identity set`() {
        requireStrictIdentityScope((1..9).map { DuplicateIdentityKey("pixiv", it.toString()) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `strict scope rejects an empty set`() {
        requireStrictIdentityScope(emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `strict scope rejects duplicate identities`() {
        requireStrictIdentityScope(List(9) { DuplicateIdentityKey("pixiv", "same") })
    }

    @Test
    fun `manifest fingerprint is deterministic and exact`() {
        val first = sha256Fingerprint("manifest")
        assertEquals(first, sha256Fingerprint("manifest"))
        assertEquals(64, first.length)
        assertNotEquals(first, sha256Fingerprint("manifest "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apply rejects a fingerprint mismatch`() {
        requireManifestFingerprint("0".repeat(64), "1".repeat(64))
    }

    @Test
    fun `completed normalization is idempotent and cleanup waits for every verified identity`() {
        assertFalse(shouldNormalize("COMPLETED"))
        assertTrue(shouldNormalize("PLANNED"))
        assertFalse(shouldStartCleanup(8, 7))
        assertTrue(shouldStartCleanup(8, 8))
    }
}
