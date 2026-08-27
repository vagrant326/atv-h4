package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The shipped text trees have no dead slots either; `:core:bench` shows the shape. */

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
        val common = requireNotNull(tree.codeOf(Symbol.Character('e'))).size
        val rare = requireNotNull(tree.codeOf(Symbol.Character('q'))).size

        assertTrue(common < rare, "'e' cost $common presses and 'q' cost $rare")
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

    /**
     * The reserved branch's whole purpose: every function costs exactly two presses, in a place
     * that does not move when the corpus does. Nothing here was placed from a frequency,
     * because none of these has one.
     */
    /**
     * Two presses for a leaf, three for anything inside the one sub-branch, and every one of
     * them in a fixed place. The depth is the whole claim: a position that moved with the corpus
     * would be a position nobody could learn.
     */
    @Test
    fun `the reserved branch puts every function where it says it does`() {
        val tree = controlTree(SAMPLE)

        for ((direction, slot) in Weights.CONTROL_BRANCH) {
            when (slot) {
                is ControlSlot.Leaf -> assertEquals(
                    listOf(Direction.UP, direction),
                    tree.codeOf(slot.symbol),
                    slot.symbol.label,
                )

                is ControlSlot.Branch -> for ((inner, symbol) in slot.slots) {
                    assertEquals(
                        listOf(Direction.UP, direction, inner),
                        tree.codeOf(symbol),
                        symbol.label,
                    )
                }
            }
        }
    }

    @Test
    fun `the reserved direction never begins a character code`() {
        val tree = controlTree(SAMPLE)
        val reserved = Weights.CONTROL_BRANCH.values.flatMap { it.symbols }.toSet()

        for (symbol in tree.symbols) {
            if (symbol !in reserved) {
                assertTrue(
                    requireNotNull(tree.codeOf(symbol)).first() != Direction.UP,
                    "${symbol.label} took a code out of the reserved branch",
                )
            }
        }
    }

    /**
     * What reserving a branch costs, as an invariant rather than as a figure: three carrying
     * branches leave twelve two-press codes instead of sixteen, and the characters cannot have
     * more than that however the frequencies fall.
     *
     * How much of it is actually spent is a measurement, and belongs to `:core:bench` — on a
     * sample this size the distribution is flat enough that Huffman does not want the room
     * anyway, so asserting a cost here would be asserting noise.
     */
    @Test
    fun `reserving a branch caps the characters at twelve two-press codes`() {
        val tree = controlTree(SAMPLE)
        val reserved = Weights.CONTROL_BRANCH.values.flatMap { it.symbols }.toSet()

        val cheap = tree.symbols
            .filter { it !in reserved }
            .count { requireNotNull(tree.codeOf(it)).size <= 2 }

        assertTrue(cheap <= (ARITY - 1) * ARITY, "$cheap characters within two presses")
    }

    @Test
    fun `digits are not in the text tree and punctuation is`() {
        val tree = controlTree(SAMPLE)

        assertNull(tree.codeOf(Symbol.Character('7')), "a digit reached the text tree")
        assertNotNull(tree.codeOf(Symbol.Character('&')), "an unseen mark lost its code")
    }

    /**
     * Twelve symbols in exactly twelve two-press slots, so every one of them costs two presses
     * and **not one direction pair leads nowhere**. A dead slot is a press that does nothing,
     * which on this method is indistinguishable from the remote not being heard.
     */
    @Test
    fun `the digit layer is two presses everywhere and has no dead slots`() {
        val layer = CodeTree.withControlBranch(
            Weights.digitLayer(),
            Weights.DIGIT_CONTROL_BRANCH,
        )

        for (character in Symbol.DIGITS + listOf('.', ',')) {
            assertEquals(
                2,
                requireNotNull(layer.codeOf(Symbol.Character(character))).size,
                "'$character'",
            )
        }
        for (first in Direction.entries) {
            val branch = layer.root.children[first.ordinal]
            assertNotNull(branch, "$first leads nowhere")
            for (second in Direction.entries) {
                assertNotNull(
                    (branch as Node.Branch).children[second.ordinal],
                    "$first then $second leads nowhere",
                )
            }
        }
        assertNull(layer.codeOf(Symbol.Character('&')), "an unwanted mark reached the layer")
    }

    private fun controlTree(text: String) = CodeTree.withControlBranch(
        Weights.text(FrequencyTable.of(text)),
        Weights.CONTROL_BRANCH,
    )

    private fun weights(text: String): Map<Symbol, Long> =
        FrequencyTable.of(text).counts.mapKeys { Symbol.Character(it.key) }

    private companion object {
        const val SAMPLE =
            "the quick brown fox jumps over the lazy dog and then does it again quietly"
    }
}
