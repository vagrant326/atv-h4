package io.github.vagrant326.atvh4.core

/**
 * How far the caret moves for a word jump, in single-character steps.
 *
 * Steps rather than an absolute offset because the editor owns the text and the selection: the
 * keyboard asks it to move left or right, which stays correct in a field the keyboard did not
 * fill and where it cannot know the caret's absolute position without asking.
 *
 * A jump is deliberately bounded by a word boundary rather than by a rate. Held-key repeat on
 * Android is roughly twenty characters a second after a four-hundred-millisecond delay, and a
 * TV query averages eleven characters — so an accelerating caret crosses the whole field before
 * the thumb reacts, and any rate slow enough to aim is no faster than pressing. Ending at the
 * boundary makes overshoot impossible instead of unlikely.
 */
object Caret {

    /**
     * Steps left to reach the start of the word before the caret. Any spaces immediately behind
     * the caret are crossed first, so a jump from just after a word lands at that word's start
     * rather than stopping on the space.
     *
     * @param before the text immediately preceding the caret, as the editor reports it.
     */
    fun stepsBack(before: CharSequence): Int {
        var at = before.length
        while (at > 0 && before[at - 1] == ' ') {
            at--
        }
        while (at > 0 && before[at - 1] != ' ') {
            at--
        }
        return before.length - at
    }

    /** Steps right to reach the end of the word after the caret. */
    fun stepsForward(after: CharSequence): Int {
        var at = 0
        while (at < after.length && after[at] == ' ') {
            at++
        }
        while (at < after.length && after[at] != ' ') {
            at++
        }
        return at
    }
}
