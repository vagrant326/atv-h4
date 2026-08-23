package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeTreeTest {

    @Test
    fun `every symbol gets a code and no code is a prefix of another`() {
        val tree = CodeTree.of(weights("the quick brown fox jumps over the lazy dog"))
        val codes = tree.symbols.map { requireNotNull(tree.codeOf(it)) }

        assertEquals(tree.symbols.size, codes.distinct().size, "codes are not unique")
        for (code in codes) {
            for (other in codes) {
                if (other.size > code.size) {
                    assertTrue(other.subList(0, code.size) != code, "$code is a prefix of $other")
                }
            }
        }
    }

    /**
     * The property the whole method rests on: walking a code from the root arrives at exactly
     * the symbol it was assigned to, never at a branch and never at a different leaf.
     */
    @Test
    fun `walking a code arrives at its own symbol`() {
        val tree = CodeTree.of(weights(SAMPLE))
        val coder = Coder(tree)

        for (symbol in tree.symbols) {
            val code = requireNotNull(tree.codeOf(symbol))
            for (step in code.dropLast(1)) {
                assertEquals(Press.Descended, coder.press(step), "${symbol.label} ended early")
            }
            assertEquals(Press.Emitted(symbol), coder.press(code.last()))
        }
    }

    @Test
    fun `frequent symbols get shorter codes than rare ones`() {
        val tree = CodeTree.of(weights(SAMPLE))
        val space = requireNotNull(tree.codeOf(Symbol.Character(' '))).size
        val rare = requireNotNull(tree.codeOf(Symbol.Character('q'))).size

        assertTrue(space < rare, "space cost $space presses and 'q' cost $rare")
    }

    /** A hash iteration order leaking into the assignment would be invisible on one machine. */
    @Test
    fun `the same table builds the same codes every time`() {
        val first = CodeTree.of(weights(SAMPLE))
        val second = CodeTree.of(weights(SAMPLE))

        for (symbol in first.symbols) {
            assertEquals(first.codeOf(symbol), second.codeOf(symbol), symbol.label)
        }
    }

    /**
     * The exact property [Ordering.PINNED] buys, and the reason [Symbol.rank] ignores
     * frequency: two distributions that give a symbol the same code length give it the same
     * code. Swapping the weights of two symbols that already cost the same leaves every code
     * length untouched, so pinning has to reproduce the whole table — where ordering by weight
     * moves both of them for no gain at all.
     *
     * How far two *real* languages agree is a measurement, not a guarantee: Polish and English
     * do not produce the same length distribution, and where the lengths differ the codes
     * after them shift. `:core:bench` reports the figure.
     */
    @Test
    fun `pinning keeps the codes still when only the frequencies move`() {
        // Seven symbols, three of them tied far above the rest: three one-press codes and four
        // two-press ones, however the tie falls. Raising one of the three cannot change a
        // single code length, so pinning has to reproduce the whole table.
        val base: Map<Symbol, Long> = mapOf(
            Symbol.Character('a') to 100L,
            Symbol.Character('b') to 100L,
            Symbol.Character('c') to 100L,
            Symbol.Character('d') to 1L,
            Symbol.Character('e') to 1L,
            Symbol.Character('f') to 1L,
            Symbol.Character('g') to 1L,
        )
        val raised = base + (Symbol.Character('b') to 300L)

        val pinned = CodeTree.of(base)
        val alsoPinned = CodeTree.of(raised)
        for (symbol in pinned.symbols) {
            assertEquals(pinned.codeOf(symbol), alsoPinned.codeOf(symbol), symbol.label)
        }

        // Ordering by weight instead hands 'b' the code 'a' had, for no gain whatever: both
        // cost one press either way. That reshuffling is what the pinned order exists to avoid.
        val byWeight = CodeTree.of(raised, Ordering.FREQUENCY)
        assertEquals(pinned.codeOf(Symbol.Character('a')), byWeight.codeOf(Symbol.Character('b')))
    }

    @Test
    fun `functions are reachable and backspace is cheap`() {
        // Enough text that the function shares round to something. They are fractions of the
        // character total, so on a sentence-sized corpus they all floor to one and the test
        // would be measuring the coercion rather than the weighting.
        val table = FrequencyTable.of(SAMPLE.repeat(200))
        val tree = CodeTree.of(Weights.text(table, CharacterSet.FULL))

        for (function in Symbol.Function.entries) {
            assertNotNull(tree.codeOf(function), "${function.label} has no code")
        }
        val backspace = requireNotNull(tree.codeOf(Symbol.Function.BACKSPACE)).size
        val language = requireNotNull(tree.codeOf(Symbol.Function.LANGUAGE)).size
        assertTrue(backspace < language, "backspace cost $backspace, language switch $language")
    }

    @Test
    fun `a zero count symbol is still typable`() {
        val tree = CodeTree.of(Weights.text(FrequencyTable.of("abc abc"), CharacterSet.FULL))

        assertNotNull(tree.codeOf(Symbol.Character('7')), "an unseen digit lost its code")
        assertNotNull(tree.codeOf(Symbol.Character('&')), "an unseen mark lost its code")
    }

    private fun weights(text: String): Map<Symbol, Long> =
        FrequencyTable.of(text).counts.mapKeys { Symbol.Character(it.key) }

    private companion object {
        const val SAMPLE =
            "the quick brown fox jumps over the lazy dog and then does it again quietly"
    }
}
