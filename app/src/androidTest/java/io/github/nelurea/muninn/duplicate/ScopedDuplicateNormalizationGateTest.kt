package io.github.nelurea.muninn.duplicate

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScopedDuplicateNormalizationGateTest {
    @Test
    fun uniqueScopeGateIsSharedWithTheDeviceRunner() {
        val identities = (1..9).map { DuplicateIdentityKey("pixiv", it.toString()) }
        requireStrictIdentityScope(identities)
        try {
            requireStrictIdentityScope(identities + identities.first())
            fail("duplicate identities must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("NON_EMPTY_UNIQUE_IDENTITY_SCOPE_REQUIRED", expected.message)
        }
    }

    @Test
    fun fingerprintComparisonIsExact() {
        val fingerprint = sha256Fingerprint("manifest")
        requireManifestFingerprint(fingerprint, sha256Fingerprint("manifest"))
        try {
            requireManifestFingerprint(fingerprint, sha256Fingerprint("manifest-changed"))
            fail("changed manifest must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("MANIFEST_FINGERPRINT_MISMATCH", expected.message)
        }
    }
}
