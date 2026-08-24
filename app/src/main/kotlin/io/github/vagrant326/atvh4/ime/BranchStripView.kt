package io.github.vagrant326.atvh4.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.vagrant326.atvh4.R
import io.github.vagrant326.atvh4.core.Direction
import io.github.vagrant326.atvh4.core.Node
import io.github.vagrant326.atvh4.core.Symbol
import io.github.vagrant326.atvh4.model.Mode
import io.github.vagrant326.atvh4.model.TreeScope
import io.github.vagrant326.atvh4.settings.HintMode

/**
 * The live view of the next branch: what each of the four directions leads to *from where the
 * user is now*, rather than the whole tree.
 *
 * This is the one piece of UI in the programme that decides whether its keyboard is usable at
 * all on the first attempt. A Huffman code is arbitrary — nothing about it can be recalled from
 * a phone, and a TV remote has nothing printed on it to help — and this build does not assume
 * the user will ever memorise it. So the guide is sized so the *first* press is never a guess,
 * and it can still be turned off by anyone whose thumb has learnt the tree anyway.
 *
 * It deliberately does not repeat the text being typed. The field already shows that, and a
 * copy here would cost a row of search results, the scarcest thing on a TV screen.
 */
@SuppressLint("ViewConstructor")
class BranchStripView(context: Context) : LinearLayout(context) {

    private val statusRow = TextView(context).apply {
        setTextColor(FOREGROUND)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    private val inlineHint = TextView(context).apply {
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    private val cells = mutableMapOf<Direction, TextView>()

    /** The path so far, in the middle of the cross where the eye already is. */
    private val centre = TextView(context).apply {
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        isSingleLine = true
    }

    private val languageValue = hintValue()
    private val deleteValue = hintValue()
    private val layerValue = hintValue()

    /** Kept so it can be hidden: under a shared tree there is no language to switch to. */
    private val languageHint = hintLine(
        context.getString(R.string.strip_hint_language),
        languageValue,
    )

    /**
     * Assigned keys, named rather than drawn into the cross. The cross is what the four
     * directions do right now; putting an optional hardware key in one of its cells would say
     * that the key is one of them.
     */
    private val hints = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(20), 0, 0, 0)
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.TOP
            topMargin = dp(2)
        }
        addView(
            hintLine(
                context.getString(R.string.strip_hint_submit),
                hintValue().apply { text = context.getString(R.string.strip_submit_key) },
            )
        )
        addView(
            hintLine(
                context.getString(R.string.strip_hint_back),
                hintValue().apply { text = context.getString(R.string.strip_back_key) },
            )
        )
        addView(hintLine(context.getString(R.string.strip_hint_delete), deleteValue))
        addView(languageHint)
        addView(hintLine(context.getString(R.string.strip_hint_layer), layerValue))
    }

    /** Left-hand spacer, so the cross stays centred with the hints beside it. */
    private val guideRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(View(context).apply { layoutParams = LayoutParams(0, 1, 1f) })
        addView(buildCross())
        addView(hints)
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(BACKGROUND)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        addView(statusRow)
        addView(inlineHint)
        addView(guideRow)
    }

    fun update(state: StripState) {
        statusRow.text = statusRow(state)

        guideRow.visibility = if (state.hintMode == HintMode.BRANCHES) VISIBLE else GONE
        inlineHint.visibility = if (state.hintMode == HintMode.INLINE) VISIBLE else GONE

        if (state.hintMode == HintMode.OFF) {
            return
        }

        // The edit mode is not a tree: one press, one action. So the guide names the actions
        // rather than drawing branches, which is also the honest picture of what changed.
        val labels = if (state.mode == Mode.EDIT) {
            Direction.entries.associateWith { editLabel(it) }
        } else {
            val branches = state.coder.branches
            Direction.entries.associateWith { branch(branches[it.ordinal]) }
        }

        if (inlineHint.visibility == VISIBLE) {
            inlineHint.text = Direction.entries.joinToString("   ") { "${it.arrow}${labels[it]}" }
            return
        }

        for (direction in Direction.entries) {
            val cell = cells.getValue(direction)
            cell.text = "${direction.arrow} ${labels[direction]}"
            val resolved = state.mode == Mode.EDIT ||
                state.coder.branches[direction.ordinal] is Node.Leaf
            cell.setTextColor(if (resolved) ACCENT else DIM)
        }
        centre.text = if (state.mode == Mode.EDIT) {
            context.getString(R.string.strip_editing)
        } else {
            state.coder.path.joinToString("") { it.arrow }
                .ifEmpty { context.getString(R.string.strip_ready) }
        }
        languageHint.visibility = if (state.scope == TreeScope.SHARED) GONE else VISIBLE
        languageValue.text = keyLabel(
            state.customKeys.language,
            context.getString(R.string.strip_in_tree),
        )
        deleteValue.text = keyLabel(
            state.customKeys.delete,
            context.getString(R.string.strip_in_tree),
        )
        layerValue.text = keyLabel(
            state.customKeys.layer,
            context.getString(R.string.strip_in_tree),
        )
    }

    private fun editLabel(direction: Direction): String = context.getString(
        when (direction) {
            Direction.LEFT -> R.string.strip_edit_left
            Direction.RIGHT -> R.string.strip_edit_right
            Direction.UP -> R.string.strip_edit_delete
            Direction.DOWN -> R.string.strip_edit_language
        }
    )

    /** A leaf names its symbol; a branch names the heaviest symbols underneath it. */
    private fun branch(node: Node?): String = when (node) {
        null -> context.getString(R.string.strip_dead_branch)
        is Node.Leaf -> display(node.symbol)
        is Node.Branch -> node.preview.joinToString("") { display(it) } + "…"
    }

    /**
     * Short enough to sit in a preview beside eleven others. Not translated: these stand for
     * keys, and a two-letter abbreviation that changed with the system language would be a
     * different symbol to learn per locale.
     */
    private fun display(symbol: Symbol): String = when (symbol) {
        is Symbol.Character ->
            if (symbol.value == ' ') context.getString(R.string.symbol_space)
            else symbol.value.toString()

        Symbol.Function.BACKSPACE -> context.getString(R.string.symbol_backspace)
        Symbol.Function.LAYER -> context.getString(R.string.symbol_layer)
        Symbol.Function.EDIT -> context.getString(R.string.symbol_edit)
    }

    private fun statusRow(state: StripState): CharSequence {
        if (state.showLanguageChooser) {
            val text = SpannableStringBuilder()
            for (language in state.enabledLanguages) {
                val start = text.length
                text.append(language.label).append("   ")
                val selected = language == state.language
                text.setSpan(
                    ForegroundColorSpan(if (selected) ACCENT else DIM),
                    start,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            return text.append(dim(context.getString(R.string.strip_chooser_hint)))
        }

        // Says so rather than looking broken: raised by the trigger key over an app that never
        // asked for input, there is nowhere to send characters.
        if (!state.hasEditor) {
            return warning(context.getString(R.string.strip_no_editor))
        }

        // Under a shared tree the tag names every language the tree covers, because that is
        // what it covers. Under per-language trees it names the one in force, and that is the
        // most useful thing on the strip: the wrong one produces valid characters.
        val active = if (state.scope == TreeScope.SHARED) {
            state.enabledLanguages.joinToString("+") { it.label }
        } else {
            state.language.label
        }
        val tag = if (state.trained) {
            active
        } else {
            context.getString(R.string.strip_no_table, active)
        }
        val text = SpannableStringBuilder(dim(tag)).append(dim("   "))
        when (state.mode) {
            Mode.DIGITS -> text.append(accent(context.getString(R.string.strip_digit_layer)))
                .append(dim("   "))

            Mode.EDIT -> text.append(accent(context.getString(R.string.strip_edit_mode)))
                .append(dim("   "))

            Mode.TEXT -> Unit
        }

        if (state.mode == Mode.EDIT) {
            return text.append(dim(context.getString(R.string.strip_edit_exit)))
        }

        val path = state.coder.path
        if (path.isEmpty()) {
            return text.append(
                if (state.deadPress) {
                    warning(context.getString(R.string.strip_nothing_there))
                } else {
                    dim(context.getString(R.string.strip_press_a_direction))
                }
            )
        }
        text.append(accent(path.joinToString(" ") { it.arrow }))
        if (state.deadPress) {
            text.append(dim("   ")).append(warning(context.getString(R.string.strip_nothing_there)))
        }
        return text
    }

    private fun keyLabel(keyCode: Int, fallback: String): String =
        if (keyCode == KeyBindings.NO_KEY) {
            fallback
        } else {
            KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        }

    private fun buildCross(): View {
        // Wide enough for a full first-press branch — about a dozen symbols — because a
        // truncated branch forces a guess, and nothing here assumes the codes are known.
        val cross = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(dp(432), LayoutParams.WRAP_CONTENT)
        }
        cross.addView(crossRow(null, cell(Direction.UP), null))
        cross.addView(crossRow(cell(Direction.LEFT), centre, cell(Direction.RIGHT)))
        cross.addView(crossRow(null, cell(Direction.DOWN), null))
        return cross
    }

    /** Three equal columns, so the arrows land where the thumb expects them. */
    private fun crossRow(left: View?, middle: View, right: View?) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(3) }
        addView(left ?: spacer())
        addView(middle)
        addView(right ?: spacer())
    }

    private fun spacer() = View(context).apply {
        layoutParams = LayoutParams(0, 1, 1f)
    }

    private fun cell(direction: Direction) = TextView(context).apply {
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(6), dp(4), dp(6), dp(4))
        setBackgroundColor(CELL)
        layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }
        cells[direction] = this
    }

    /** Two columns, so the values line up instead of drifting with label length. */
    private fun hintLine(label: String, value: TextView) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(2) }
        addView(
            TextView(context).apply {
                text = label
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LayoutParams(dp(66), LayoutParams.WRAP_CONTENT)
            }
        )
        addView(value)
    }

    private fun hintValue() = TextView(context).apply {
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun dim(text: String) = colour(text, DIM)

    private fun accent(text: String) = colour(text, ACCENT)

    private fun warning(text: String) = colour(text, WARNING)

    private fun colour(text: String, colour: Int) =
        SpannableStringBuilder(text).apply {
            setSpan(ForegroundColorSpan(colour), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xF0101014.toInt()
        const val FOREGROUND = 0xFFE8E8EC.toInt()
        const val DIM = 0xFF80808C.toInt()
        const val MUTED = 0xFF6B6B78.toInt()
        const val ACCENT = 0xFF7FD1FF.toInt()
        const val WARNING = 0xFFEF9F27.toInt()
        const val CELL = 0xFF1A1A22.toInt()
    }
}
