package io.github.vagrant326.atvh4.core

/**
 * Whether the next letter is a capital, and for how long.
 *
 * One position in the reserved branch rather than two, because the branch had one to spare and
 * not two. The order is not a matter of taste — isolated capitals, being sentence openings and
 * proper nouns, outnumber runs of them in both alphabets — so the first press buys the common
 * case and the lock costs one more.
 *
 * [ONCE] cannot be forgotten, because it spends itself on the letter it capitalised. That
 * matters more here than in the keypad keyboards: a press in this method is invisible until the
 * code completes, so a mode the user did not mean to be in produces a character that reads as a
 * mistyped code rather than as a mode.
 *
 * **Presentation only.** The code tree, the frequency table and every code in it stay in lower
 * case: `a` and `A` are the same leaf at the same depth, and the case is what the editor is
 * told. Nothing here can move a code, so nothing here can move a measured KSPC.
 */
enum class LetterCase {

    LOWER,

    /** The next letter, and then back to [LOWER] on its own. */
    ONCE,

    /** Every letter until switched off. */
    LOCKED,
    ;

    fun next(): LetterCase = entries[(ordinal + 1) % entries.size]

    /**
     * Marks, digits and the space come back unchanged, which is why this is applied to every
     * character the keyboard emits rather than only to letters: a caller that has to ask first
     * is a caller that will eventually forget to.
     *
     * `uppercaseChar` rather than `uppercase`: the locale-aware version returns a *string*, to
     * cover the languages where one letter becomes two — none of which occur in either alphabet
     * here — and on a Turkish device it would make `İ` out of `i`. The whole Polish set maps one
     * to one, `ł` to `Ł` included.
     */
    fun apply(character: Char): Char = if (this == LOWER) character else character.uppercaseChar()

    /**
     * What the state becomes once a capital has actually reached the field.
     *
     * Only a letter spends [ONCE]. A space, a mark or a digit cannot be capitalised, so
     * consuming the state on one would quietly take back the capital the user asked for — and
     * a space is two presses away in the same branch, so that is not a rare sequence.
     */
    fun afterLetter(): LetterCase = if (this == ONCE) LOWER else this
}
