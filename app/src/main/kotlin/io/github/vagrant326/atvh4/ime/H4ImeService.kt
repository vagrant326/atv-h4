package io.github.vagrant326.atvh4.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import io.github.vagrant326.atvh4.core.Caret
import io.github.vagrant326.atvh4.core.Coder
import io.github.vagrant326.atvh4.core.CodeTree
import io.github.vagrant326.atvh4.core.Direction
import io.github.vagrant326.atvh4.core.Press
import io.github.vagrant326.atvh4.core.Symbol
import io.github.vagrant326.atvh4.model.Language
import io.github.vagrant326.atvh4.model.Mode
import io.github.vagrant326.atvh4.model.TreeRepository
import io.github.vagrant326.atvh4.model.TreeScope
import io.github.vagrant326.atvh4.settings.Preferences

/**
 * Four directions, and nothing to disambiguate.
 *
 * There is no composing text here and no candidate to accept. A completed code emits exactly
 * one symbol, so the character appears in the field the moment it is decided and never has to
 * be taken back. What the keyboard owns between presses is a position in the tree, and nothing
 * else — the editor owns the text.
 *
 * The first press is reserved. `UP` is not a code symbol: it opens a fixed branch holding
 * space, delete, the digit layer and the edit mode, all at two presses, in the same place
 * forever. That costs the letters a quarter of their two-press codes — measured at about 5% of
 * all presses — and buys three things: no invented frequencies for functions that appear in no
 * corpus, four positions that can be learnt where fifty cannot, and a tree whose long tail is
 * spread over three branches instead of piled into one.
 */
class H4ImeService : InputMethodService() {

    private lateinit var trees: TreeRepository
    private lateinit var preferences: Preferences
    private lateinit var coder: Coder
    private var strip: BranchStripView? = null
    private var language = Language.PL
    private var mode = Mode.TEXT
    private var showLanguageChooser = false
    private var deadPress = false

    override fun onCreate() {
        super.onCreate()
        trees = TreeRepository(this)
        preferences = Preferences(this)
        language = preferences.activeLanguage
        coder = Coder(textTree())
    }

    override fun onCreateInputView(): View =
        BranchStripView(this).also { strip = it }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        showLanguageChooser = false
        deadPress = false
        if (language !in preferences.enabledLanguages) {
            language = preferences.activeLanguage
        }
        // Recomputed for every field rather than remembered: a mode entered in a PIN box must
        // not follow the user into the next search query.
        mode = if (wantsDigits(info)) Mode.DIGITS else Mode.TEXT
        coder.use(currentTree())
        moveCaretToEnd(info)
        render()
    }

    /**
     * A field that declares itself numeric goes straight to the digit layer, where every digit
     * is two presses instead of four or more.
     *
     * Plenty of fields that mostly hold digits still declare themselves plain text — a
     * Downloader code box is one — which is why the layer is also two presses away by hand.
     */
    private fun wantsDigits(info: EditorInfo?): Boolean {
        val variant = (info?.inputType ?: return false) and InputType.TYPE_MASK_CLASS
        return variant == InputType.TYPE_CLASS_NUMBER ||
            variant == InputType.TYPE_CLASS_PHONE ||
            variant == InputType.TYPE_CLASS_DATETIME
    }

    /**
     * A field that opens with the caret at the front is almost never what was wanted: coming
     * back to a search box means adding to the query, not prefixing it.
     */
    private fun moveCaretToEnd(info: EditorInfo?) {
        if (info == null || info.initialSelStart != 0 || info.initialSelEnd != 0) {
            return
        }
        val connection = currentInputConnection ?: return
        val tail = connection.getTextAfterCursor(MAX_TAIL, 0)?.length ?: 0
        if (tail > 0) {
            connection.setSelection(tail, tail)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // An IME receives hardware key events even while its window is hidden. Consuming d-pad
        // events in that state takes over navigation for the whole device - it left a TV
        // unnavigable, recoverable only via HOME or a USB mouse. This keyboard's code symbols
        // *are* the d-pad, so that risk is larger here than anywhere else in the programme.
        //
        // So while hidden exactly one key is honoured: the trigger the user assigned, which is
        // unassigned by default and can never be a reserved key.
        if (!isInputViewShown) {
            val trigger = preferences.triggerKeyCode
            if (trigger != KeyBindings.NO_KEY && keyCode == trigger && event.repeatCount == 0) {
                raiseSelf()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        val action = KeyBindings.of(
            keyCode,
            event.repeatCount,
            preferences.customKeys,
            editing = mode == Mode.EDIT,
        ) ?: return super.onKeyDown(keyCode, event)

        if (action == Action.Ignore) {
            return true
        }

        if (showLanguageChooser && handleChooser(action)) {
            render()
            return true
        }

        when (action) {
            is Action.Code ->
                if (mode == Mode.EDIT) edit(action.direction) else press(action.direction)

            is Action.WordJump -> jumpWord(action.direction)
            Action.Submit -> submit()
            Action.Delete -> backspace()
            Action.NextLanguage -> stepLanguage(1)
            Action.ShowLanguages -> openChooser()
            Action.ToggleLayer -> enterMode(if (mode == Mode.DIGITS) Mode.TEXT else Mode.DIGITS)
            Action.Ignore -> Unit
            Action.Back -> if (!goBack()) return true
        }

        render()
        return true
    }

    /**
     * `BACK`, in order: drop a half-finished code, then leave a mode, then leave the keyboard.
     *
     * Abandoning cannot be a code — mid-code every further press descends to some leaf, so
     * there is no cancel inside the tree — which is why this is the one non-d-pad key the
     * method genuinely needs.
     *
     * @return false when the keyboard has hidden itself and there is nothing left to draw.
     */
    private fun goBack(): Boolean {
        deadPress = false
        if (coder.abandon()) {
            return true
        }
        if (mode != Mode.TEXT) {
            enterMode(Mode.TEXT)
            return true
        }
        requestHideSelf(0)
        return false
    }

    private fun press(direction: Direction) {
        when (val press = coder.press(direction)) {
            is Press.Emitted -> {
                deadPress = false
                emit(press.symbol)
            }

            Press.Descended -> deadPress = false

            // Unused code space. Huffman pads the leaf count up to a whole number of merges,
            // so a handful of directions lead nowhere and pressing one is a normal thing to do.
            Press.Dead -> deadPress = true
        }
    }

    private fun emit(symbol: Symbol) {
        when (symbol) {
            is Symbol.Character -> {
                currentInputConnection?.commitText(symbol.value.toString(), 1)
                // A space is the digit layer's own way out. It was going to be typed anyway, so
                // leaving costs nothing — which is most of why the layer is sticky at all.
                if (symbol.value == ' ' && mode == Mode.DIGITS) {
                    enterMode(Mode.TEXT)
                }
            }

            Symbol.Function.BACKSPACE -> backspace()
            Symbol.Function.LAYER -> enterMode(Mode.DIGITS)
            Symbol.Function.EDIT -> enterMode(Mode.EDIT)
        }
    }

    /**
     * The edit mode: one press, one action, no code to complete.
     *
     * Caret movement is forwarded to the editor rather than computed here. The editor owns the
     * text and the selection, and asking it to move is the only version that stays correct in a
     * field this keyboard did not fill.
     */
    private fun edit(direction: Direction) {
        deadPress = false
        when (direction) {
            Direction.LEFT -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            Direction.RIGHT -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            Direction.UP -> backspace()
            Direction.DOWN -> openChooser()
        }
    }

    /**
     * A held caret moves by a whole word, as a fixed number of single steps.
     *
     * Single steps rather than a computed selection: the caret's absolute position is not
     * something the keyboard can know without asking, and asking the editor to move is the only
     * version that stays correct in a field it did not fill. The count comes from the text the
     * editor reports, so a jump ends on a word boundary and cannot overshoot.
     */
    private fun jumpWord(direction: Direction) {
        deadPress = false
        val connection = currentInputConnection ?: return
        val steps = when (direction) {
            Direction.LEFT ->
                Caret.stepsBack(connection.getTextBeforeCursor(WORD_SCAN, 0) ?: return)

            Direction.RIGHT ->
                Caret.stepsForward(connection.getTextAfterCursor(WORD_SCAN, 0) ?: return)

            else -> return
        }
        val key = if (direction == Direction.LEFT) {
            KeyEvent.KEYCODE_DPAD_LEFT
        } else {
            KeyEvent.KEYCODE_DPAD_RIGHT
        }
        repeat(steps) { sendDownUpKeyEvents(key) }
    }

    private fun enterMode(next: Mode) {
        mode = next
        coder.use(currentTree())
    }

    private fun backspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    /**
     * Raises the keyboard without an app having asked for it, which is the whole of what the
     * trigger key does.
     *
     * `requestShowSelf` puts the window on screen, but an IME writes through an
     * `InputConnection` and a view that never requested input does not provide one - so over an
     * app that renders its own keyboard, the keys arrive and there is nowhere to send them. The
     * strip says which of the two happened rather than leaving it to guesswork.
     */
    private fun raiseSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestShowSelf(0)
        }
    }

    /** A list rather than blind cycling: with more than two languages, cycling is unusable. */
    private fun openChooser() {
        if (preferences.treeScope == TreeScope.SHARED) {
            return
        }
        showLanguageChooser = preferences.enabledLanguages.size > 1
    }

    /**
     * While the list is open the language changes live, so up and down are the whole
     * interaction and there is nothing to confirm. Returns false for keys that should close the
     * list and then be handled normally.
     */
    private fun handleChooser(action: Action): Boolean = when (action) {
        is Action.Code -> when (action.direction) {
            Direction.UP -> { stepLanguage(-1); true }
            Direction.DOWN -> { stepLanguage(1); true }
            Direction.LEFT, Direction.RIGHT -> { showLanguageChooser = false; true }
        }

        Action.NextLanguage, Action.ShowLanguages -> { stepLanguage(1); true }
        Action.Submit -> { showLanguageChooser = false; true }
        Action.Back -> { showLanguageChooser = false; true }
        else -> false
    }

    /**
     * Steps through the languages the user enabled, not through everything the app knows.
     *
     * The switch is a mode, and here a mode error is expensive in a way it is not for a
     * predictive keyboard: the same presses in the other tree produce a valid but different
     * character, so the mistake arrives looking like a typo. That is why the tag on the strip is
     * permanent, and why the two trees are built to agree on as many codes as Huffman allows.
     */
    private fun stepLanguage(delta: Int) {
        val enabled = preferences.enabledLanguages
        // One tree covering every language has nothing to switch between, and a key that
        // silently does nothing is worse than one that does not exist.
        if (enabled.size < 2 || preferences.treeScope == TreeScope.SHARED) {
            return
        }
        val index = enabled.indexOf(language).coerceAtLeast(0)
        language = enabled[(index + delta + enabled.size) % enabled.size]
        preferences.activeLanguage = language
        coder.use(currentTree())
    }

    /** The edit mode has no tree; the text tree stays loaded underneath it. */
    private fun currentTree(): CodeTree =
        if (mode == Mode.DIGITS) trees.digitTree else textTree()

    private fun textTree(): CodeTree {
        val set = preferences.characterSet
        return if (preferences.treeScope == TreeScope.SHARED) {
            trees.sharedTree(preferences.enabledLanguages, set)
        } else {
            trees.textTree(language, set)
        }
    }

    private fun render() {
        strip?.update(
            StripState(
                coder = coder,
                language = language,
                enabledLanguages = preferences.enabledLanguages,
                scope = preferences.treeScope,
                trained = preferences.enabledLanguages.all { trees.isTrained(it) },
                hintMode = preferences.hintMode,
                showLanguageChooser = showLanguageChooser,
                customKeys = preferences.customKeys,
                hasEditor = currentInputConnection != null,
                mode = mode,
                deadPress = deadPress,
            )
        )
    }

    private fun submit() {
        val connection = currentInputConnection ?: return
        val editorAction = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (editorAction != null && editorAction != EditorInfo.IME_ACTION_NONE) {
            connection.performEditorAction(editorAction)
        } else {
            connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }

    private companion object {
        /** Only used to find the end of an existing value, so a generous bound is plenty. */
        const val MAX_TAIL = 2000

        /**
         * How far to look for a word boundary. A TV query is eleven characters on average; this
         * is long enough that the bound is never what stops a jump, and short enough that the
         * editor is not asked for a document.
         */
        const val WORD_SCAN = 64
    }
}
