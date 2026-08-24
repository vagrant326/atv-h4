package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoderTest {

    private val tree = CodeTree.of(
        Weights.text(FrequencyTable.of(SAMPLE))
    )

    @Test
    fun `a completed code emits exactly one symbol`() {
        val coder = Coder(tree)
        val emitted = type(coder, Symbol.Character('a'))

        assertEquals(Symbol.Character('a'), emitted)
        assertFalse(coder.hasPartialCode, "the path should reset once a code completes")
    }

    @Test
    fun `a partial code keeps the path and shows where each direction leads`() {
        val coder = Coder(tree)
        val code = requireNotNull(tree.codeOf(Symbol.Character('q')))
        coder.press(code.first())

        assertEquals(listOf(code.first()), coder.path)
        assertEquals(ARITY, coder.branches.size)
        assertTrue(coder.branches.any { it != null }, "a branch on the path leads nowhere at all")
    }

    @Test
    fun `abandoning reports whether there was anything to abandon`() {
        val coder = Coder(tree)

        assertFalse(coder.abandon())
        coder.press(requireNotNull(tree.codeOf(Symbol.Character('q'))).first())
        assertTrue(coder.abandon())
        assertFalse(coder.hasPartialCode)
    }

    /**
     * Half a code means nothing in another tree, so a switch drops it. Replaying the presses
     * would produce a character the user never aimed at — the mode error docs/20-h4writer.md
     * warns is this method's worst failure, arriving as a typo rather than as a mode problem.
     */
    @Test
    fun `switching tree drops the partial code`() {
        val coder = Coder(tree)
        coder.press(requireNotNull(tree.codeOf(Symbol.Character('q'))).first())

        coder.use(CodeTree.of(Weights.digitLayer()))

        assertFalse(coder.hasPartialCode)
        assertEquals(Symbol.Character('7'), type(coder, Symbol.Character('7')))
    }

    @Test
    fun `a direction that leads nowhere is reported rather than swallowed`() {
        // Two symbols leave two of the four root branches unused, which is the smallest tree
        // that can have a dead branch at all.
        val small = CodeTree.of(
            mapOf(Symbol.Character('a') to 2L, Symbol.Character('b') to 1L)
        )
        val coder = Coder(small)
        val dead = Direction.entries.first { direction ->
            small.root.children[direction.ordinal] == null
        }

        assertEquals(Press.Dead, coder.press(dead))
    }

    private fun type(coder: Coder, symbol: Symbol): Symbol? {
        val code = requireNotNull(coder.tree.codeOf(symbol))
        var emitted: Symbol? = null
        for (step in code) {
            val press = coder.press(step)
            if (press is Press.Emitted) {
                emitted = press.symbol
            }
        }
        return emitted
    }

    private companion object {
        const val SAMPLE =
            "the quick brown fox jumps over the lazy dog and then does it again quietly"
    }
}
