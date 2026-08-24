package io.github.vagrant326.atvh4.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

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
 * Turns a character table into the weight map a [CodeTree] is built from.
 *
 * There is nothing invented left in here. Every weight is a measured character count, and every
 * function reaches the user through structure instead: a reserved branch for the three that
 * need a code, and the edit mode for the two that are better as a held mode than as a code.
 * The earlier version of this file assigned delete 3% and the language switch 0.1% — numbers
 * that no corpus contains and that Huffman nevertheless treated as data.
 */
object Weights {

    /**
     * The reserved branch: one fixed first press, four fixed second presses.
     *
     * Space is here rather than in the tree because it costs two presses either way at the
     * frequency a TV query actually has, so it may as well be somewhere the user can find
     * without reading.
     */
    val CONTROL_BRANCH: Map<Direction, Symbol> = mapOf(
        Direction.UP to Symbol.Character(' '),
        Direction.DOWN to Symbol.Function.BACKSPACE,
        Direction.LEFT to Symbol.Function.LAYER,
        Direction.RIGHT to Symbol.Function.EDIT,
    )

    /**
     * The same idea one layer down. No edit mode and no second layer switch: the way out of the
     * digit layer is a space or `BACK`, and offering a third route would only be another thing
     * to read.
     */
    val DIGIT_CONTROL_BRANCH: Map<Direction, Symbol> = mapOf(
        Direction.UP to Symbol.Character(' '),
        Direction.DOWN to Symbol.Function.BACKSPACE,
    )

    /**
     * Letters and punctuation: no functions, no space, no digits. All four live elsewhere.
     *
     * Punctuation belongs **here**, not with the digits. There was briefly a setting that moved
     * it to the second layer in exchange for shorter letter codes, and it measured as a dead
     * heat on held-out titles — 2.361 against 2.363 for English, 2.397 against 2.395 for Polish
     * — because the layer trips cost exactly what the shorter letters saved. A setting that
     * rebuilds every code for nothing is worse than no setting, so it went, and with it the
     * reason the digit layer ever carried an apostrophe.
     */
    fun text(table: FrequencyTable): Map<Symbol, Long> {
        val weights = LinkedHashMap<Symbol, Long>()
        for ((character, count) in table.counts) {
            if (character.isLetter() || character in Symbol.PUNCTUATION) {
                weights[Symbol.Character(character)] = count
            }
        }
        // A mark the corpus never produced still has to be typable, so membership is fixed and
        // the count only decides depth.
        for (mark in Symbol.PUNCTUATION) {
            weights.getOrPut(Symbol.Character(mark)) { 0L }
        }
        return weights
    }

    /**
     * The second layer: ten digits, and nothing else to compete with them.
     *
     * Ten characters over three carrying branches fit in twelve two-press slots exactly, so
     * **every digit costs two presses** — which is the entire reason the layer exists, since a
     * run of digits is a PIN, a pairing code or a seven-digit sideload code.
     *
     * The weight is uniform because there is no corpus of "digits a person types into a TV".
     * That is the absence of information stated honestly rather than a fabricated frequency, and
     * with ten symbols in twelve slots it changes nothing anyway.
     */
    fun digitLayer(): Map<Symbol, Long> =
        Symbol.DIGITS.associate { Symbol.Character(it) to DIGIT_WEIGHT }

    /** Arbitrary: only ratios reach Huffman, and with a uniform table there are none. */
    private const val DIGIT_WEIGHT = 1L
}
