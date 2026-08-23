package io.github.vagrant326.atvh4.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Which characters the text layer's tree carries. */
enum class CharacterSet {
    /**
     * Letters and space. Shorter codes for the letters, and the configuration MacKenzie's
     * 2.074 was measured in — so it is the comparison point against the literature, not the
     * honest cost of a usable keyboard. Punctuation and digits are still typable: they live in
     * the second layer, and reaching them costs the switch there and back.
     */
    LETTERS,

    /**
     * Letters, space, punctuation and digits in one tree. Every character costs slightly more
     * and nothing needs a layer switch. This is the 2.321 configuration and the default,
     * because it is what a keyboard actually needs.
     */
    FULL,
}

/**
 * Character counts for one language, as built by `corpus/count.py`.
 *
 * Counts rather than a finished tree, on purpose. The tree is small enough to build at
 * startup, and building it on the device from the same code the simulator uses is what stops
 * a measured figure and a typed figure from drifting apart. Shipping a pre-built tree would
 * put a second implementation of the method in the pipeline.
 *
 * Format, big-endian:
 *
 *     magic     4 bytes  "H4F1"
 *     count     u16      number of entries
 *     reserved  u16      0
 *     entries   count x (u16 UTF-16 code unit, u64 count)
 */
class FrequencyTable(val counts: Map<Char, Long>) {

    init {
        require(counts.isNotEmpty()) { "an empty frequency table cannot produce a tree" }
    }

    val total: Long = counts.values.sum()

    val characters: Set<Char> get() = counts.keys

    fun countOf(character: Char): Long = counts[character] ?: 0L

    fun write(output: OutputStream) {
        val stream = DataOutputStream(output)
        stream.write(MAGIC.encodeToByteArray())
        stream.writeShort(counts.size)
        stream.writeShort(0)
        for ((character, count) in counts.entries.sortedBy { it.key }) {
            stream.writeChar(character.code)
            stream.writeLong(count)
        }
        stream.flush()
    }

    companion object {

        const val MAGIC = "H4F1"

        fun read(input: InputStream): FrequencyTable {
            val stream = DataInputStream(input.buffered())
            val magic = ByteArray(4)
            stream.readFully(magic)
            require(magic.decodeToString() == MAGIC) {
                "not a frequency table: magic was ${magic.decodeToString()}"
            }
            val count = stream.readUnsignedShort()
            stream.readUnsignedShort()
            require(count > 1) { "a table of $count characters cannot produce a tree" }
            val counts = LinkedHashMap<Char, Long>(count)
            repeat(count) {
                val character = stream.readUnsignedShort().toChar()
                counts[character] = stream.readLong()
            }
            return FrequencyTable(counts)
        }

        /** Counts the characters of [text] directly. Used by the tests and by the bench. */
        fun of(text: CharSequence): FrequencyTable {
            val counts = LinkedHashMap<Char, Long>()
            for (character in text) {
                counts[character] = (counts[character] ?: 0L) + 1L
            }
            return FrequencyTable(counts)
        }

        /** Both tables summed, for the merged-tree configuration the bench compares against. */
        fun merge(tables: Iterable<FrequencyTable>): FrequencyTable {
            val counts = LinkedHashMap<Char, Long>()
            for (table in tables) {
                for ((character, count) in table.counts) {
                    counts[character] = (counts[character] ?: 0L) + count
                }
            }
            return FrequencyTable(counts)
        }
    }
}

/**
 * Turns a character table into the weight map a [CodeTree] is built from, which means
 * deciding what the functions are worth.
 *
 * Those weights are the one judgement call in an otherwise mechanical method, and they are
 * assumptions rather than measurements: how often a user deletes, moves the caret or changes
 * mode is a property of the user, not of the language. They are stated here as shares of the
 * character total so they survive a change of corpus size, and they are identical for every
 * language so that the way out of the wrong language never moves.
 *
 * Backspace is deliberately expensive to get wrong in both directions. Too light and it
 * steals a two-press code from a letter; too heavy and every correction costs four presses
 * on a method whose own documentation warns that errors surface one character late.
 */
object Weights {

    val FUNCTION_SHARE: Map<Symbol.Function, Double> = mapOf(
        Symbol.Function.BACKSPACE to 0.030,
        Symbol.Function.CARET_LEFT to 0.003,
        Symbol.Function.CARET_RIGHT to 0.003,
        Symbol.Function.LANGUAGE to 0.001,
        Symbol.Function.LAYER to 0.002,
    )

    val TEXT_FUNCTIONS: Set<Symbol.Function> = Symbol.Function.entries.toSet()

    /**
     * One tree covering every language has no language to switch to, so the switch is not a
     * symbol. Which is most of the appeal: the mode that cannot be in the wrong position is
     * the one that cannot produce a valid character the user never aimed at.
     */
    val SHARED_FUNCTIONS: Set<Symbol.Function> = TEXT_FUNCTIONS - Symbol.Function.LANGUAGE

    /** No language switch down here: the digit layer has no letters to switch between. */
    val DIGIT_FUNCTIONS: Set<Symbol.Function> = setOf(
        Symbol.Function.BACKSPACE,
        Symbol.Function.CARET_LEFT,
        Symbol.Function.CARET_RIGHT,
        Symbol.Function.LAYER,
    )

    fun text(
        table: FrequencyTable,
        set: CharacterSet,
        functions: Set<Symbol.Function> = TEXT_FUNCTIONS,
    ): Map<Symbol, Long> {
        val weights = LinkedHashMap<Symbol, Long>()
        for ((character, count) in table.counts) {
            if (keep(character, set)) {
                weights[Symbol.Character(character)] = count
            }
        }
        // A character the corpus never produced still has to be typable, so the filter above
        // works from the character set rather than from what happens to have a count. Titles
        // are full of characters running speech does not contain.
        if (set == CharacterSet.FULL) {
            for (character in Symbol.PUNCTUATION + Symbol.DIGITS) {
                weights.getOrPut(Symbol.Character(character)) { 0L }
            }
        }
        return weights + functionWeights(table.total, functions)
    }

    /**
     * The second layer: everything that is not a letter. Ten digits, seven marks, space and
     * the functions.
     *
     * It has two jobs. For [CharacterSet.LETTERS] it is the only way to reach punctuation and
     * digits at all, so it has to carry the complete set — a keyboard that cannot type an
     * ampersand is not typing "bohren &amp; der club of gore", which is a real query from the
     * corpus. For [CharacterSet.FULL] it is a shortcut for a *run* of digits — a PIN, a
     * pairing code, the seven digits of a Downloader shortcode — where two presses each beats
     * three or four.
     *
     * Weights are flat within a class rather than measured. There is no corpus for "digits a
     * person types into a TV", and inventing one would move which digit is cheapest by half a
     * press. Digits and space outrank the marks so that the run of digits, which is the reason
     * this layer exists, is the thing that lands at two presses.
     */
    fun digitLayer(): Map<Symbol, Long> {
        val weights = LinkedHashMap<Symbol, Long>()
        for (digit in Symbol.DIGITS) {
            weights[Symbol.Character(digit)] = DIGIT_WEIGHT
        }
        weights[Symbol.Character(' ')] = DIGIT_WEIGHT
        for (mark in Symbol.PUNCTUATION) {
            weights[Symbol.Character(mark)] = MARK_WEIGHT
        }
        return weights + functionWeights(weights.values.sum(), DIGIT_FUNCTIONS)
    }

    private fun functionWeights(total: Long, functions: Set<Symbol.Function>): Map<Symbol, Long> =
        functions.associateWith { function ->
            val share = FUNCTION_SHARE[function] ?: 0.0
            (total * share).toLong().coerceAtLeast(1L)
        }

    private fun keep(character: Char, set: CharacterSet): Boolean = when {
        character == ' ' || character.isLetter() -> true
        else -> set == CharacterSet.FULL
    }

    /**
     * Arbitrary in absolute terms — only the ratios reach Huffman, and the function shares are
     * taken against the total, so the scale never shows up anywhere.
     */
    private const val DIGIT_WEIGHT = 1000L

    private const val MARK_WEIGHT = 200L
}
