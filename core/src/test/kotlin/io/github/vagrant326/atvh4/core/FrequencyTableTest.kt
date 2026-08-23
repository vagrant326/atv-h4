package io.github.vagrant326.atvh4.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrequencyTableTest {

    /**
     * Pins the format from both ends. `corpus/count.py` writes these bytes and this reads
     * them; a silent disagreement would produce a keyboard whose codes are assigned from
     * nonsense while looking entirely healthy.
     */
    @Test
    fun `a table survives a write and a read`() {
        val original = FrequencyTable.of("zażółć gęślą jaźń")
        val bytes = ByteArrayOutputStream().also { original.write(it) }.toByteArray()

        val restored = FrequencyTable.read(ByteArrayInputStream(bytes))

        assertEquals(original.counts, restored.counts)
        assertEquals(original.total, restored.total)
    }

    @Test
    fun `the header is checked rather than trusted`() {
        val bytes = "NOPE".encodeToByteArray() + ByteArray(16)

        val failure = runCatching { FrequencyTable.read(ByteArrayInputStream(bytes)) }

        assertTrue(failure.isFailure, "a file with the wrong magic was accepted")
    }

    /**
     * Polish diacritics are characters in their own right here, with their own codes. There is
     * no ambiguity to inflate, only a slightly longer code for a rarer character — which is
     * why this method can afford them where a keypad partition has to argue about it.
     */
    @Test
    fun `polish diacritics get their own codes`() {
        val tree = CodeTree.of(
            Weights.text(FrequencyTable.of(POLISH), CharacterSet.FULL)
        )

        for (letter in "ąćęłńóśźż") {
            assertNotNull(tree.codeOf(Symbol.Character(letter)), "'$letter' has no code")
        }
        assertEquals(
            9,
            "ąćęłńóśźż".map { requireNotNull(tree.codeOf(Symbol.Character(it))) }.distinct().size,
        )
    }

    private companion object {
        const val POLISH =
            "zażółć gęślą jaźń a potem jeszcze raz cicho i bez pośpiechu w tę samą stronę"
    }
}
