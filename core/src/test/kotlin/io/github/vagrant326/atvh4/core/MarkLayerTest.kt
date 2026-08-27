package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkLayerTest {

    /** Every printable mark on a US QWERTY keyboard, which is the promise this layer makes. */
    private val qwerty = "`~!@#$%^&*()-_=+[]{}\\|;:'\",.<>/?"

    private fun tree() = CodeTree.withControlBranch(
        Weights.markLayer(),
        Weights.DIGIT_CONTROL_BRANCH,
    )

    @Test
    fun `the layer carries the whole QWERTY set`() {
        assertEquals(qwerty.toSet(), Symbol.MARKS.toSet())
        for (mark in qwerty) {
            assertTrue(
                tree().codeOf(Symbol.Character(mark)) != null,
                "$mark is unreachable, which is the whole bug this layer fixes",
            )
        }
    }

    @Test
    fun `the seven in the text tree are here too`() {
        // The layer is the whole set rather than the remainder, so there is one thing to know
        // about where a mark lives. The slots are there: three carrying branches give
        // forty-eight codes at three presses and thirty-two symbols do not fill them.
        for (mark in Symbol.PUNCTUATION) {
            assertTrue(mark in Symbol.MARKS, "$mark should be in the layer as well")
        }
    }

    @Test
    fun `no mark costs more than three presses`() {
        val tree = tree()
        for (mark in Symbol.MARKS) {
            val code = requireNotNull(tree.codeOf(Symbol.Character(mark)))
            assertTrue(code.size <= 3, "$mark costs ${code.size} presses")
        }
    }

    @Test
    fun `the reserved branch means the same thing here as in the text tree`() {
        // `↑←←` is the digit layer wherever you are, `↑←↑` is the case, `↑↓` deletes. A position
        // that meant one thing in one layer and another elsewhere would be the one part of the
        // tree a user learns, made unlearnable.
        val text = CodeTree.withControlBranch(
            Weights.text(FrequencyTable.of("the quick brown fox jumps over the lazy dog")),
            Weights.CONTROL_BRANCH,
        )
        val marks = tree()
        for (symbol in Weights.CONTROL_BRANCH.values.flatMap { it.symbols }) {
            assertEquals(text.codeOf(symbol), marks.codeOf(symbol), symbol.label)
        }
    }

    @Test
    fun `the case switch and the layers sit under one direction`() {
        val branch = Weights.CONTROL_BRANCH[Direction.LEFT]
        assertTrue(branch is ControlSlot.Branch)
        assertEquals(
            setOf(Symbol.Function.SHIFT, Symbol.Function.LAYER, Symbol.Function.MARKS),
            (branch as ControlSlot.Branch).slots.values.toSet(),
        )
    }

    @Test
    fun `space, delete and the edit mode did not move`() {
        // The point of putting the three new functions under `↑←` rather than rearranging: the
        // positions already learnt are exactly where they were.
        assertEquals(
            ControlSlot.Leaf(Symbol.Character(' ')),
            Weights.CONTROL_BRANCH[Direction.UP],
        )
        assertEquals(
            ControlSlot.Leaf(Symbol.Function.BACKSPACE),
            Weights.CONTROL_BRANCH[Direction.DOWN],
        )
        assertEquals(
            ControlSlot.Leaf(Symbol.Function.EDIT),
            Weights.CONTROL_BRANCH[Direction.RIGHT],
        )
    }
}
