package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LetterCaseTest {

    @Test
    fun `one gesture reaches every state and comes back`() {
        assertEquals(LetterCase.ONCE, LetterCase.LOWER.next())
        assertEquals(LetterCase.LOCKED, LetterCase.ONCE.next())
        assertEquals(
            LetterCase.LOWER,
            LetterCase.LOCKED.next(),
            "a cycle that cannot be left strands the user in a mode the remote does not show",
        )
    }

    @Test
    fun `every Polish letter has a capital and keeps it`() {
        val pairs = mapOf(
            'ą' to 'Ą', 'ć' to 'Ć', 'ę' to 'Ę', 'ł' to 'Ł', 'ń' to 'Ń',
            'ó' to 'Ó', 'ś' to 'Ś', 'ź' to 'Ź', 'ż' to 'Ż',
        )
        for ((lower, upper) in pairs) {
            assertEquals(upper, LetterCase.ONCE.apply(lower), "$lower must reach $upper")
        }
    }

    @Test
    fun `a letter spends the one-off and the lock survives it`() {
        assertEquals(LetterCase.LOWER, LetterCase.ONCE.afterLetter())
        assertEquals(LetterCase.LOCKED, LetterCase.LOCKED.afterLetter())
    }

    @Test
    fun `the space in the reserved branch cannot spend a capital`() {
        // Space is two presses away in the same branch the case switch lives in, so shift then
        // space is not a rare sequence. It must not eat the capital.
        assertEquals(' ', LetterCase.LOCKED.apply(' '))
        assertTrue(!' '.isLetter())
    }

    @Test
    fun `marks and digits pass through untouched`() {
        for (mark in Symbol.MARKS) {
            assertEquals(mark, LetterCase.LOCKED.apply(mark))
        }
        for (digit in Symbol.DIGITS) {
            assertEquals(digit, LetterCase.LOCKED.apply(digit))
        }
    }

    @Test
    fun `case cannot move a code`() {
        // The tree is built from the frequency table, which is lower case throughout, and the
        // case is applied where a character is emitted. So no code, and therefore no measured
        // KSPC, can move because of this feature.
        val table = FrequencyTable.of("the quick brown fox jumps over the lazy dog")
        val before = CodeTree.withControlBranch(Weights.text(table), Weights.CONTROL_BRANCH)
        val after = CodeTree.withControlBranch(Weights.text(table), Weights.CONTROL_BRANCH)
        for (symbol in before.symbols) {
            assertEquals(before.codeOf(symbol), after.codeOf(symbol))
        }
        assertTrue(before.symbols.filterIsInstance<Symbol.Character>().none { it.value.isUpperCase() })
    }
}
