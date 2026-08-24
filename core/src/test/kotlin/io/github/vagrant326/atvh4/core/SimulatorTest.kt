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

    private fun text() = CodeTree.withControlBranch(
        Weights.text(table),
        Weights.CONTROL_BRANCH,
    )

    @Test
    fun `the cost of a string is the sum of its code lengths`() {
        val tree = text()
        val target = "the fox"
        val expected = target.sumOf { requireNotNull(tree.codeOf(Symbol.Character(it))).size }

        val result = Simulator(tree, digits).run(target)

        assertEquals(target.length, result.characters)
        assertEquals(expected, result.codePresses)
        assertEquals(0, result.layerPresses, "nothing here needs the digit layer")
    }

    @Test
    fun `a run of digits pays for one switch, not one per digit`() {
        val tree = text()
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
        val tree = text()
        val entry = requireNotNull(tree.codeOf(Symbol.Function.LAYER)).size

        val result = Simulator(tree, digits).run("blade runner 2049 remastered")

        assertEquals(entry, result.layerPresses, "leaving should have been free")
    }

    /** Anything other than a space leaves through BACK, which is one real press. */
    @Test
    fun `leaving the layer for a letter costs one press`() {
        val tree = text()
        val entry = requireNotNull(tree.codeOf(Symbol.Function.LAYER)).size

        val result = Simulator(tree, digits).run("a7a")

        assertEquals(entry + 1, result.layerPresses)
    }

    /**
     * Punctuation lives in the text tree, not with the digits — an apostrophe in the middle of a
     * title is not worth a layer switch, and the setting that used to move it there measured as
     * a dead heat.
     */
    @Test
    fun `punctuation never needs the digit layer`() {
        val result = Simulator(text(), digits).run("bohren & der club of gore, don't")

        assertEquals(0, result.layerPresses)
        assertTrue(result.codePresses > 0)
    }

    /** Ten digits over three carrying branches fit twelve slots, so none of them costs more. */
    @Test
    fun `every digit is two presses`() {
        for (digit in Symbol.DIGITS) {
            assertEquals(
                2,
                requireNotNull(digits.codeOf(Symbol.Character(digit))).size,
                "'$digit'",
            )
        }
    }

    @Test
    fun `space is two presses in both trees`() {
        val tree = text()

        assertEquals(2, requireNotNull(tree.codeOf(Symbol.Character(' '))).size)
        assertEquals(2, requireNotNull(digits.codeOf(Symbol.Character(' '))).size)
    }

    private companion object {
        const val SAMPLE =
            "the quick brown fox jumps over the lazy dog and then does it again quietly"
    }
}
