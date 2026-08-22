package io.github.nelurea.muninn.media.move

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIdContainmentTest {
    @Test
    fun `accepts root and descendants with document id boundaries`() {
        assertTrue(DocumentIdContainment.contains("primary:Pictures", "primary:Pictures"))
        assertTrue(DocumentIdContainment.contains("primary:Pictures", "primary:Pictures/Muninn/image.jpg"))
        assertTrue(DocumentIdContainment.contains("primary:", "primary:Pictures/image.jpg"))
    }

    @Test
    fun `rejects textual prefix sibling and another volume`() {
        assertFalse(DocumentIdContainment.contains("primary:Pictures", "primary:Pictures-old/image.jpg"))
        assertFalse(DocumentIdContainment.contains("primary:Pictures", "0123-4567:Pictures/image.jpg"))
    }
}
