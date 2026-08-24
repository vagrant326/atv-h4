package io.github.vagrant326.atvh4.ime

import android.view.KeyEvent
import io.github.vagrant326.atvh4.core.Direction

sealed interface Action {

    /** One of the four code symbols. */
    data class Code(val direction: Direction) : Action

    /**
     * A whole word, from holding the caret in the edit mode. Only ever produced there: holding
     * a direction while typing must never repeat, because a code symbol that arrives twice
     * emits a character the user did not ask for.
     */
    data class WordJump(val direction: Direction) : Action

    data object Submit : Action

    /** Abandon the partial code if there is one, otherwise leave. */
    data object Back : Action

    data object Delete : Action
    data object NextLanguage : Action
    data object ShowLanguages : Action
    data object ToggleLayer : Action

    /** Consume the event and do nothing. */
    data object Ignore : Action
}

/**
 * Custom bindings, because remotes disagree about which keys exist and about what they
 * report. The user's `TEXT` key sits where a phone has `*` and reports keycode 300, well
 * outside the standard range — nothing in the app could have guessed that.
 *
 * Every one of these is optional, and not in the way that word is usually meant: backspace,
 * caret movement, the language switch and the layer switch are all leaves of the code tree,
 * so a remote with nothing but a d-pad, a centre button and BACK can reach the whole keyboard.
 * A key assigned here buys presses on a remote that happens to have a spare button. The
 * exception is the trigger, which cannot be a code because the keyboard is not on screen yet.
 */
data class CustomKeys(val language: Int, val delete: Int, val trigger: Int, val layer: Int)

object KeyBindings {

    const val NO_KEY = 0

    /**
     * Keys the keyboard needs for itself.
     *
     * Shorter than the equivalent list in the keypad-based keyboards: this method never reads
     * a number key, so `0`-`9` are free to be assigned here. On a remote that has them, that
     * is the one advantage of a numeric remote this application can actually use.
     */
    val RESERVED: Set<Int> = buildSet {
        add(KeyEvent.KEYCODE_DPAD_UP)
        add(KeyEvent.KEYCODE_DPAD_DOWN)
        add(KeyEvent.KEYCODE_DPAD_LEFT)
        add(KeyEvent.KEYCODE_DPAD_RIGHT)
        add(KeyEvent.KEYCODE_DPAD_CENTER)
        add(KeyEvent.KEYCODE_ENTER)
        add(KeyEvent.KEYCODE_BACK)
        add(KeyEvent.KEYCODE_HOME)
    }

    /**
     * @param repeatCount straight from the [KeyEvent]. Only `1` counts as a hold; later repeats
     *   are swallowed, so one hold is one action rather than a rate. That is what keeps a held
     *   caret from crossing the whole field — Android repeats at roughly twenty a second and a
     *   TV query averages eleven characters.
     * @param editing whether the edit mode is in force, which is the only place a held
     *   direction means anything. While typing, every repeat is swallowed on every key: a held
     *   code symbol that repeated would walk the tree on its own and commit text nobody asked
     *   for, and unlike an ambiguous keypad there is no wrong candidate to walk past afterwards.
     */
    fun of(keyCode: Int, repeatCount: Int, custom: CustomKeys, editing: Boolean): Action? {
        val longPress = repeatCount == 1

        if (custom.language != NO_KEY && keyCode == custom.language) {
            return when {
                repeatCount > 1 -> Action.Ignore
                longPress -> Action.ShowLanguages
                else -> Action.NextLanguage
            }
        }

        if (longPress && editing) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Action.WordJump(Direction.LEFT)
                KeyEvent.KEYCODE_DPAD_RIGHT -> Action.WordJump(Direction.RIGHT)
                else -> Action.Ignore
            }
        }

        if (repeatCount > 0) {
            return Action.Ignore
        }

        if (custom.delete != NO_KEY && keyCode == custom.delete) {
            return Action.Delete
        }
        if (custom.layer != NO_KEY && keyCode == custom.layer) {
            return Action.ToggleLayer
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Action.Code(Direction.UP)
            KeyEvent.KEYCODE_DPAD_LEFT -> Action.Code(Direction.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> Action.Code(Direction.RIGHT)
            KeyEvent.KEYCODE_DPAD_DOWN -> Action.Code(Direction.DOWN)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> Action.Submit
            KeyEvent.KEYCODE_DEL -> Action.Delete
            KeyEvent.KEYCODE_BACK -> Action.Back
            else -> null
        }
    }
}
