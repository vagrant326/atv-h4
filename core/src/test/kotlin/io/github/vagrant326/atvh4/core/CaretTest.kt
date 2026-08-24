package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CaretTest {

    @Test
    fun `a jump back lands on the start of the word it was inside`() {
        assertEquals(3, Caret.stepsBack("kung fu pan"))
    }

    /** From just after a word, the space is crossed rather than landed on. */
    @Test
    fun `a jump back from a space reaches the previous word`() {
        // "kung fu |" -> "kung |fu ", which is the space plus the two letters of "fu".
        assertEquals(3, Caret.stepsBack("kung fu "))
    }

    @Test
    fun `a jump back at the front of the field does nothing`() {
        assertEquals(0, Caret.stepsBack(""))
    }

    @Test
    fun `a jump back with no space reaches the front`() {
        assertEquals(11, Caret.stepsBack("electroboom"))
    }

    @Test
    fun `a jump forward lands on the end of the word it was inside`() {
        assertEquals(3, Caret.stepsForward("nda quiz"))
    }

    @Test
    fun `a jump forward from a space reaches the next word`() {
        assertEquals(5, Caret.stepsForward(" quiz"))
    }

    @Test
    fun `a jump forward at the end of the field does nothing`() {
        assertEquals(0, Caret.stepsForward(""))
    }

    /**
     * Runs of spaces are crossed in one jump. Nothing in this keyboard produces them, but a
     * field it did not fill can contain anything.
     */
    @Test
    fun `runs of spaces are crossed in one jump`() {
        assertEquals(5, Caret.stepsBack("fu   "))
        assertEquals(5, Caret.stepsForward("   fu"))
    }
}
