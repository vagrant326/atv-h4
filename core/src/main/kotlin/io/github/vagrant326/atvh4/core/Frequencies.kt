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
    val CONTROL_BRANCH: Map<Direction, ControlSlot> = mapOf(
        Direction.UP to ControlSlot.Leaf(Symbol.Character(' ')),
        Direction.DOWN to ControlSlot.Leaf(Symbol.Function.BACKSPACE),

        /**
         * `↑←` was the layer switch and is now the branch of everything that changes what the
         * keys produce: the case, the digits and the marks. That grouping is why nothing else
         * moved — space, delete and the edit mode are exactly where they were, and the one
         * position that changed meaning had already been the mode key.
         *
         * The bill is a third press for each of the three, digits included, where the digit
         * layer used to be two. It is paid once per field rather than once per character, and
         * it is the same price a Huffman leaf would have charged: `corpus/fetch.py` normalises
         * before writing, so the corpus on disk has no capital in it and there is no measured
         * frequency for a shift to be placed from — the honest options were structure at three
         * presses or a re-fetched corpus that would re-measure every published figure.
         */
        Direction.LEFT to ControlSlot.Branch(
            mapOf(
                Direction.UP to Symbol.Function.SHIFT,
                Direction.LEFT to Symbol.Function.LAYER,
                Direction.RIGHT to Symbol.Function.MARKS,
            )
        ),

        Direction.RIGHT to ControlSlot.Leaf(Symbol.Function.EDIT),
    )

    /**
     * The digit layer uses the **same** reserved branch, which is the point: `↑←` switches layer
     * in both directions, `↑↓` deletes in both, `↑↑` is space in both. Four positions, one
     * meaning each, wherever you are.
     *
     * It started as a shorter branch holding only space and delete, which left `↑←` and `↑→`
     * leading nowhere — two dead slots in the one part of the tree a user actually learns.
     * Filling them with the functions they already have in the text tree costs nothing and
     * removes both.
     */
    val DIGIT_CONTROL_BRANCH: Map<Direction, ControlSlot> = CONTROL_BRANCH

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
     * The second layer: ten digits, a full stop and a comma.
     *
     * Three carrying branches give exactly twelve two-press slots, and ten digits would leave
     * two of them leading nowhere. A dead slot is a press that does nothing, which on a method
     * where a press is invisible until the code completes is indistinguishable from the remote
     * not being heard — so the two spare slots go to the marks a numeric field actually wants,
     * a decimal point and a thousands comma. **Every symbol in this layer is two presses**, and
     * none of the twelve leads nowhere.
     *
     * The weight is uniform because there is no corpus of "digits a person types into a TV".
     * That is the absence of information stated honestly rather than a fabricated frequency, and
     * with twelve symbols in twelve slots it changes nothing anyway.
     */
    fun digitLayer(): Map<Symbol, Long> =
        (Symbol.DIGITS + listOf('.', ',')).associate { Symbol.Character(it) to DIGIT_WEIGHT }

    /**
     * The mark layer: every printable mark a QWERTY keyboard carries, all thirty-two.
     *
     * All of them rather than the twenty-five the text tree leaves out, so there is one rule —
     * this layer is the whole set. A layer holding "the marks that did not fit elsewhere" would
     * be a list nobody could predict the contents of, and the seven that also live in the text
     * tree cost nothing here: three carrying branches give forty-eight slots at three presses
     * and thirty-two symbols do not fill them.
     *
     * Uniform, for the same reason [digitLayer] is: no corpus records how often somebody types
     * a brace into a television, and a weight invented for one would be exactly the fabrication
     * this file exists to have got rid of. What that costs is a mark at two or three presses
     * inside the layer according to where Huffman happens to put it, rather than according to
     * anything true about marks.
     */
    fun markLayer(): Map<Symbol, Long> =
        Symbol.MARKS.associate { Symbol.Character(it) to DIGIT_WEIGHT }

    /** Arbitrary: only ratios reach Huffman, and with a uniform table there are none. */
    private const val DIGIT_WEIGHT = 1L
}
