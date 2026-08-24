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

    /**
     * Two rows per direction: what one more press finishes, and what is further down. Sorted
     * for reading rather than by frequency — the question is "is my letter in here", and a
     * frequency-ordered list is the worst possible arrangement for answering it.
     */
    private class Cell(val view: LinearLayout, val immediate: TextView, val deeper: TextView)

    private val cells = mutableMapOf<Direction, Cell>()

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

        if (inlineHint.visibility == VISIBLE) {
            inlineHint.text = SpannableStringBuilder().apply {
                for (direction in Direction.entries) {
                    append(direction.arrow)
                    append(if (state.mode == Mode.EDIT) editLabel(direction) else oneLine(state, direction))
                    append("   ")
                }
            }
            return
        }

        for (direction in Direction.entries) {
            val cell = cells.getValue(direction)
            // The edit mode is not a tree: one press, one action. So there is no "deeper" row
            // to draw, which is also the honest picture of what changed.
            if (state.mode == Mode.EDIT) {
                cell.immediate.text = "${direction.arrow} ${editLabel(direction)}"
                cell.immediate.setTextColor(FUNCTION)
                cell.deeper.visibility = GONE
                continue
            }
            fill(cell, direction, state.coder.branches[direction.ordinal])
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

    /**
     * A leaf finishes here; a branch shows what one more press finishes on the top row, and
     * everything further down on the second.
     */
    private fun fill(cell: Cell, direction: Direction, node: Node?) {
        val arrow = "${direction.arrow} "
        when (node) {
            null -> {
                cell.immediate.text = arrow + context.getString(R.string.strip_dead_branch)
                cell.immediate.setTextColor(DIM)
                cell.deeper.visibility = GONE
            }

            is Node.Leaf -> {
                cell.immediate.text = SpannableStringBuilder(arrow).append(display(node.symbol))
                cell.immediate.setTextColor(ACCENT)
                cell.deeper.visibility = GONE
            }

            is Node.Branch -> {
                // Spaced on the top row, which is short and worth reading one item at a time;
                // packed on the second, which can hold twenty and needs the width.
                cell.immediate.text = SpannableStringBuilder(arrow)
                    .append(row(node.immediate, spaced = true))
                cell.immediate.setTextColor(ACCENT)
                val deeper = node.deeper
                cell.deeper.visibility = if (deeper.isEmpty()) GONE else VISIBLE
                cell.deeper.text = row(deeper, spaced = false)
            }
        }
    }

    /**
     * A run of symbols, sorted for scanning: letters and digits in order, then punctuation,
     * then functions. Frequency order was the previous arrangement and is the worst possible
     * one for the only question being asked — is my letter in here — because it puts the answer
     * somewhere unpredictable.
     *
     * Characters are concatenated without separators so a branch of twenty still fits; the
     * brackets and colour on functions are what stop them joining the run.
     */
    private fun row(symbols: List<Symbol>, spaced: Boolean): CharSequence =
        SpannableStringBuilder().apply {
            val ordered = symbols.sortedWith(
                compareBy(
                    { it is Symbol.Function },
                    { (it as? Symbol.Character)?.value?.isLetterOrDigit() == false },
                    { Symbol.rank(it) },
                )
            )
            for ((at, symbol) in ordered.withIndex()) {
                if (spaced && at > 0) {
                    append(" ")
                }
                append(display(symbol))
            }
        }

    /** The compact one-row form, which can only afford what the next press finishes. */
    private fun oneLine(state: StripState, direction: Direction): CharSequence =
        when (val node = state.coder.branches[direction.ordinal]) {
            null -> context.getString(R.string.strip_dead_branch)
            is Node.Leaf -> display(node.symbol)
            is Node.Branch -> row(node.immediate, spaced = true)
        }

    /**
     * Characters as themselves; functions bracketed and in their own colour.
     *
     * The separation is not decoration. Characters in a branch preview run together with no
     * separator — twelve of them have to fit one cell — so an unbracketed `DEL` sitting among
     * them reads as the letters d, e and l, which is exactly the wrong thing to tell someone
     * who is reading the guide to find out what a direction types.
     *
     * The function names are not translated: they stand for keys, and an abbreviation that
     * changed with the system language would be a different symbol to learn per locale.
     */
    private fun display(symbol: Symbol): CharSequence = when (symbol) {
        is Symbol.Character ->
            if (symbol.value == ' ') context.getString(R.string.symbol_space)
            else symbol.value.toString()

        Symbol.Function.BACKSPACE -> function(R.string.symbol_backspace)
        Symbol.Function.LAYER -> function(R.string.symbol_layer)
        Symbol.Function.EDIT -> function(R.string.symbol_edit)
    }

    private fun function(resource: Int) = colour(context.getString(resource), FUNCTION)

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
        // Wide enough for a whole root branch — twenty-odd symbols over two lines — because a
        // truncated branch forces a guess, and nothing here assumes the codes are known. The
        // strip is taller for it, which docs/20-h4writer.md §4 already names as this method's
        // standing cost against the search results underneath.
        val cross = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(dp(560), LayoutParams.WRAP_CONTENT)
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

    private fun cell(direction: Direction): View {
        val immediate = TextView(context).apply {
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        // Two lines, because a root branch can hold twenty symbols and a list that says "and
        // some others" does not answer the question it was asked.
        val deeper = TextView(context).apply {
            setTextColor(DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }
        val view = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setBackgroundColor(CELL)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            addView(immediate)
            addView(deeper)
        }
        cells[direction] = Cell(view, immediate, deeper)
        return view
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

        /** Functions, so they never read as characters. Distinct from the accent and the warning. */
        const val FUNCTION = 0xFFB6A0FF.toInt()
    }
}
