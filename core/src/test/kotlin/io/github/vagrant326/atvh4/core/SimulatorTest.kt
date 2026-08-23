package io.github.vagrant326.atvh4.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimulatorTest {

    private val table = FrequencyTable.of(SAMPLE)

    @Test
    fun `the cost of a string is the sum of its code lengths`() {
        val tree = CodeTree.of(Weights.text(table, CharacterSet.FULL))
        val target = "the fox"
        val expected = target.sumOf { requireNotNull(tree.codeOf(Symbol.Character(it))).size }

        val result = Simulator(tree).run(target)

        assertEquals(target.length, result.characters)
        assertEquals(expected, result.codePresses)
        assertEquals(0, result.layerPresses, "nothing here needs the digit layer")
    }

    /**
     * The letters-and-space tree cannot type an apostrophe at all, so charging the layer round
     * trip is what keeps its KSPC comparable with the full tree's. Without it the shorter tree
     * would win every comparison by having a smaller alphabet.
     */
    @Test
    fun `reaching a character outside the text tree costs the layer switch`() {
        val letters = CodeTree.of(Weights.text(table, CharacterSet.LETTERS))
        val digits = CodeTree.of(Weights.digitLayer())
        val switch = requireNotNull(letters.codeOf(Symbol.Function.LAYER)).size
        val back = requireNotNull(digits.codeOf(Symbol.Function.LAYER)).size

        val result = Simulator(letters, digits).run("a7a")

        assertEquals(switch + back, result.layerPresses)
    }

    @Test
    fun `a run of digits pays for one switch, not one per digit`() {
        val letters = CodeTree.of(Weights.text(table, CharacterSet.LETTERS))
        val digits = CodeTree.of(Weights.digitLayer())

        val one = Simulator(letters, digits).run("a7")
        val seven = Simulator(letters, digits).run("a8662742")

        assertEquals(one.layerPresses, seven.layerPresses)
    }

    @Test
    fun `the full tree costs more per letter and needs no layer switch`() {
        val letters = CodeTree.of(Weights.text(table, CharacterSet.LETTERS))
        val full = CodeTree.of(Weights.text(table, CharacterSet.FULL))
        val target = "the quick brown fox"

        val short = Simulator(letters).run(target)
        val long = Simulator(full).run(target)

        assertTrue(
            short.totalPresses <= long.totalPresses,
            "the smaller alphabet cost more: ${short.totalPresses} against ${long.totalPresses}",
        )
        assertEquals(0, Simulator(full).run("don't 8662742").layerPresses)
    }

    private companion object {
        const val SAMPLE =
            "the quick brown fox jumps over the lazy dog and then does it again quietly"
    }
}
