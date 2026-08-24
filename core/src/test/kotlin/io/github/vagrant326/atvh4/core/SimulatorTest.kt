package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimulatorTest {

    private val table = FrequencyTable.of(SAMPLE)
    private val digits = CodeTree.withControlBranch(
        Weights.digitLayer(),
        Weights.DIGIT_CONTROL_BRANCH,
    )

    private fun text(set: CharacterSet) = CodeTree.withControlBranch(
        Weights.text(table, set),
        Weights.CONTROL_BRANCH,
    )

    @Test
    fun `the cost of a string is the sum of its code lengths`() {
        val tree = text(CharacterSet.FULL)
        val target = "the fox"
        val expected = target.sumOf { requireNotNull(tree.codeOf(Symbol.Character(it))).size }

        val result = Simulator(tree, digits).run(target)

        assertEquals(target.length, result.characters)
        assertEquals(expected, result.codePresses)
        assertEquals(0, result.layerPresses, "nothing here needs the digit layer")
    }

    @Test
    fun `a run of digits pays for one switch, not one per digit`() {
        val tree = text(CharacterSet.FULL)
        val entry = requireNotNull(tree.codeOf(Symbol.Function.LAYER)).size

        val one = Simulator(tree, digits).run("a7")
        val seven = Simulator(tree, digits).run("a8662742")

        assertEquals(entry, one.layerPresses)
        assertEquals(one.layerPresses, seven.layerPresses)
    }

    /**
     * The layer is sticky and a space is its own way out, so a title with digits in the middle
     * pays for going in and nothing for coming back — the space was going to be typed anyway.
     */
    @Test
    fun `a space leaves the digit layer without costing a switch`() {
        val tree = text(CharacterSet.FULL)
        val entry = requireNotNull(tree.codeOf(Symbol.Function.LAYER)).size

        val result = Simulator(tree, digits).run("blade runner 2049 remastered")

        assertEquals(entry, result.layerPresses, "leaving should have been free")
    }

    /** Anything other than a space leaves through BACK, which is one real press. */
    @Test
    fun `leaving the layer for a letter costs one press`() {
        val tree = text(CharacterSet.FULL)
        val entry = requireNotNull(tree.codeOf(Symbol.Function.LAYER)).size

        val result = Simulator(tree, digits).run("a7a")

        assertEquals(entry + 1, result.layerPresses)
    }

    /**
     * Charging the round trip is what keeps the two character sets comparable. Without it the
     * smaller alphabet would win every comparison by being unable to type an apostrophe.
     */
    @Test
    fun `punctuation costs a layer trip when the tree has no room for it`() {
        val letters = text(CharacterSet.LETTERS)
        val full = text(CharacterSet.FULL)

        assertTrue(Simulator(letters, digits).run("don't").layerPresses > 0)
        assertEquals(0, Simulator(full, digits).run("don't").layerPresses)
    }

    @Test
    fun `space is two presses in both trees`() {
        val tree = text(CharacterSet.FULL)

        assertEquals(2, requireNotNull(tree.codeOf(Symbol.Character(' '))).size)
        assertEquals(2, requireNotNull(digits.codeOf(Symbol.Character(' '))).size)
    }

    private companion object {
        const val SAMPLE =
            "the quick brown fox jumps over the lazy dog and then does it again quietly"
    }
}
