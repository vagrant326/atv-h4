package io.github.vagrant326.atvh4.core

/**
 * One leaf of the code tree: a character, or one of the three functions that have a code.
 *
 * The list of functions is short on purpose. Delete, the digit layer and the edit mode sit in
 * the reserved branch at a fixed two presses each; caret movement and the language switch are
 * not here at all, because they live inside the edit mode where a direction means one thing and
 * costs one press. Nothing in this file has an invented frequency, which is the whole point:
 * no corpus records how often somebody corrects a typo or enters a PIN, so anything placed by
 * Huffman would have been placed from a number that was made up.
 *
 * [label] is for tests, the bench output and the printed code table. What the strip shows comes
 * from string resources instead, because those names are read by a user.
 */
sealed interface Symbol {

    val label: String

    data class Character(val value: Char) : Symbol {
        override val label: String get() = if (value == ' ') "_" else value.toString()
    }

    /**
     * Bracketed, everywhere, including here. In a branch preview the characters run together
     * with no separator, so a bare `DEL` reads as the letters d, e and l — which is the wrong
     * answer to the only question the guide is asked: what does this direction type?
     */
    enum class Function(override val label: String) : Symbol {
        BACKSPACE("[del]"),

        /** To the digit layer and back. */
        LAYER("[123]"),

        /** To the mark layer and back: everything a QWERTY keyboard prints that is not a letter. */
        MARKS("[!@#]"),

        /** `abc` → `Abc` → `ABC` → `abc`. */
        SHIFT("[Aa]"),

        /** To the edit mode: caret, delete, language. */
        EDIT("[edit]"),
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

        /**
         * The seven marks that live in the text tree, frequency-weighted like any letter. The
         * same seven the other keyboards in the programme cycle on their `1` key, so the shared
         * query corpus is typable character for character in every app.
         */
        val PUNCTUATION: List<Char> = ".,-'&:/".toList()

        val DIGITS: List<Char> = "0123456789".toList()

        /**
         * Every printable mark on a QWERTY keyboard, for the mark layer. Includes the seven in
         * [PUNCTUATION]: the layer is the whole set rather than the remainder, so there is one
         * thing to know about where a mark lives.
         */
        val MARKS: List<Char> = "`~!@#$%^&*()-_=+[]{}\\|;:'\",.<>/?".toList()
    }
}
