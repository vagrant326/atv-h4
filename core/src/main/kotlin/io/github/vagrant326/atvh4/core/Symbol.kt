package io.github.vagrant326.atvh4.core

/**
 * One leaf of the code tree.
 *
 * Functions are leaves like any character, which is the whole of H4-Writer's answer to a
 * remote that has nothing but a d-pad: if a thing needs a button it does not get one, it gets
 * a code. Backspace, caret movement and the two mode switches are therefore in the frequency
 * table, competing for short codes on the same terms as the letters.
 *
 * [label] is for tests, the bench output and the printed code table. What the strip shows
 * comes from string resources instead, because these names are read by a user.
 */
sealed interface Symbol {

    val label: String

    data class Character(val value: Char) : Symbol {
        override val label: String get() = if (value == ' ') "_" else value.toString()
    }

    enum class Function(override val label: String) : Symbol {
        BACKSPACE("DEL"),
        CARET_LEFT("<"),
        CARET_RIGHT(">"),
        LANGUAGE("LANG"),

        /** Between letters and the digit layer, in both directions. */
        LAYER("#"),
    }

    companion object {
        /**
         * Total order over every symbol, used to pin which code a symbol gets among the codes
         * of its own length.
         *
         * Ordering by code point rather than by frequency is deliberate. Code *lengths* come
         * from Huffman and are optimal for the language; which of the equal-length codes each
         * symbol gets is free, so it is spent on making the languages agree where they can.
         * Frequency order would spend it on nothing: it reshuffles equal-cost codes according
         * to the one thing that differs between two languages, so two trees that could have
         * been almost the same are gratuitously different.
         *
         * This buys agreement, it does not guarantee it. Where two languages give a symbol
         * different code *lengths* the codes after it shift, and no assignment rule can prevent
         * that without giving up the optimal lengths. How far Polish and English actually agree
         * is therefore a measurement — `:core:bench` reports it — not a property.
         *
         * Code point order also never shuffles when a language is added. A hand-written
         * preference order would, and every user who had learnt the old one would find their
         * fingers wrong.
         */
        fun rank(symbol: Symbol): Int = when (symbol) {
            is Function -> symbol.ordinal
            is Character -> Function.entries.size + symbol.value.code
        }

        val PUNCTUATION: List<Char> = ".,-'&:/".toList()

        val DIGITS: List<Char> = "0123456789".toList()
    }
}
