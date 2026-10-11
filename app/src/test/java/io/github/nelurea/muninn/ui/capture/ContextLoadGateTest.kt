package io.github.nelurea.muninn.ui.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ContextLoadGateTest {
    @Test
    fun `work result is rejected after switching work`() {
        val gate = ContextLoadGate()
        gate.bindTarget(1, 10)
        val first = requireNotNull(gate.beginLoad(ContextLoadSection.PURPOSE, 1))
        gate.bindTarget(2, 20)

        assertFalse(gate.mayApply(first))
        assertNull(gate.beginLoad(ContextLoadSection.PURPOSE, 1))
        assertNotNull(gate.beginLoad(ContextLoadSection.PURPOSE, 2))
    }

    @Test
    fun `page result is rejected after switching media`() {
        val gate = ContextLoadGate()
        gate.bindTarget(1, 10)
        val first = requireNotNull(gate.beginLoad(ContextLoadSection.FOCUS, 1, 10))
        gate.bindTarget(1, 11)

        assertFalse(gate.mayApply(first))
        assertNull(gate.beginLoad(ContextLoadSection.FOCUS, 1, 10))
        assertNotNull(gate.beginLoad(ContextLoadSection.FOCUS, 1, 11))
    }

    @Test
    fun `work result remains valid on page change`() {
        val gate = ContextLoadGate()
        gate.bindTarget(1, 10)
        val workLoad = requireNotNull(
            gate.beginLoad(ContextLoadSection.WORK_ATTRACTIONS, 1)
        )
        gate.bindTarget(1, 11)

        assertTrue(gate.mayApply(workLoad))
    }

    @Test
    fun `returning to same target does not revive older result`() {
        val gate = ContextLoadGate()
        gate.bindTarget(1, 10)
        val oldWork = requireNotNull(gate.beginLoad(ContextLoadSection.RESPONSE, 1))
        val oldPage = requireNotNull(gate.beginLoad(ContextLoadSection.PAGE_ATTRACTIONS, 1, 10))
        gate.bindTarget(2, 20)
        gate.bindTarget(1, 10)

        assertFalse(gate.mayApply(oldWork))
        assertFalse(gate.mayApply(oldPage))
    }

    @Test
    fun `newer read wins when requests complete out of order`() {
        val gate = ContextLoadGate()
        gate.bindTarget(1, 10)
        val earlier = requireNotNull(gate.beginLoad(ContextLoadSection.PURPOSE, 1))
        val later = requireNotNull(gate.beginLoad(ContextLoadSection.PURPOSE, 1))

        assertFalse(gate.mayApply(earlier))
        assertTrue(gate.mayApply(later))
    }

    @Test
    fun `mutation target captures intended media and does not drift`() {
        val gate = ContextLoadGate()
        gate.bindTarget(1, 10)
        val action = requireNotNull(gate.captureMedia(1, 10))
        gate.bindTarget(1, 11)

        assertFalse(gate.isTargetCurrent(action))
        assertTrue(gate.isTargetCurrent(requireNotNull(gate.captureMedia(1, 11))))
    }

    @Test
    fun `missing page is a distinct scope`() {
        val gate = ContextLoadGate()
        gate.bindTarget(1, null)
        val noPage = requireNotNull(gate.beginLoad(ContextLoadSection.FOCUS, 1, null))
        gate.bindTarget(1, 10)

        assertFalse(gate.mayApply(noPage))
        assertNull(gate.captureMedia(1, null))
    }
}
