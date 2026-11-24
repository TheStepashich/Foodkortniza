package net.potatom.foodkortniza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOrderTest {
    @Test
    fun movesTabForwardAndBackward() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), TabOrder.move(ids, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), TabOrder.move(ids, 3, 0))
    }

    @Test
    fun ignoresInvalidIndices() {
        val ids = listOf("a", "b")
        assertEquals(ids, TabOrder.move(ids, 0, 0))
        assertEquals(ids, TabOrder.move(ids, -1, 1))
        assertEquals(ids, TabOrder.move(ids, 0, 9))
    }

    @Test
    fun hitTestUsesWidthNotMissingArrayIndex() {
        assertTrue(TabOrder.containsX(15f, left = 10, width = 96))
        assertTrue(TabOrder.containsX(106f, left = 10, width = 96))
        assertFalse(TabOrder.containsX(107f, left = 10, width = 96))
        assertFalse(TabOrder.containsX(9f, left = 10, width = 96))
    }
}
