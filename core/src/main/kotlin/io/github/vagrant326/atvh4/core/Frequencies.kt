package io.github.vagrant326.atvh4.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Which characters the text layer's tree carries. Digits are never among them. */
enum class CharacterSet {
    /**
     * Letters only. Shorter codes for the letters; punctuation joins the digits in the second
     * layer and costs the switch there and back.
     */
    LETTERS,

    /**
     * Letters and the seven punctuation marks. The default: an apostrophe in the middle of a
     * title is not worth a layer switch.
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

    /** Characters only: no functions, no space, no digits. All four live elsewhere. */
    fun text(table: FrequencyTable, set: CharacterSet): Map<Symbol, Long> {
        val weights = LinkedHashMap<Symbol, Long>()
        for ((character, count) in table.counts) {
            if (character.isLetter() || (set == CharacterSet.FULL && character in Symbol.PUNCTUATION)) {
                weights[Symbol.Character(character)] = count
            }
        }
        // A mark the corpus never produced still has to be typable, so the set decides
        // membership and the count only decides depth.
        if (set == CharacterSet.FULL) {
            for (mark in Symbol.PUNCTUATION) {
                weights.getOrPut(Symbol.Character(mark)) { 0L }
            }
        }
        return weights
    }

    /**
     * The second layer: digits and punctuation.
     *
     * Digits are weighted equally and above the marks. That is not a fabricated frequency, it
     * is the absence of one stated honestly — there is no corpus of "digits a person types into
     * a TV", and the ordering that matters is only that a run of digits, the reason this layer
     * exists, lands at two presses.
     *
     * Punctuation is here as well as in the full text tree. The redundancy is deliberate: the
     * layer has to be self-sufficient for [CharacterSet.LETTERS], where it is the only route to
     * an apostrophe.
     */
    fun digitLayer(): Map<Symbol, Long> {
        val weights = LinkedHashMap<Symbol, Long>()
        for (digit in Symbol.DIGITS) {
            weights[Symbol.Character(digit)] = DIGIT_WEIGHT
        }
        for (mark in Symbol.PUNCTUATION) {
            weights[Symbol.Character(mark)] = MARK_WEIGHT
        }
        return weights
    }

    /**
     * Arbitrary in absolute terms — only the ratio reaches Huffman, and there are no shares
     * taken against a total any more, so the scale never shows up anywhere.
     */
    private const val DIGIT_WEIGHT = 1000L

    private const val MARK_WEIGHT = 200L
}
