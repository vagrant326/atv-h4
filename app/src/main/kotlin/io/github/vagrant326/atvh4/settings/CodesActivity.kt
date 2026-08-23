package io.github.vagrant326.atvh4.settings

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.vagrant326.atvh4.R
import io.github.vagrant326.atvh4.core.Direction
import io.github.vagrant326.atvh4.core.FrequencyTable
import io.github.vagrant326.atvh4.core.Node
import io.github.vagrant326.atvh4.core.Symbol
import io.github.vagrant326.atvh4.core.Weights
import io.github.vagrant326.atvh4.model.Language
import io.github.vagrant326.atvh4.model.TreeRepository
import io.github.vagrant326.atvh4.model.TreeScope

/**
 * The whole code table, on screen.
 *
 * In every other keyboard in the programme this would be documentation and would live in a
 * README. Here the table *is* the interface: it is what the user memorises, a base-4 Huffman
 * code cannot be guessed from anything they already know, and a TV remote has nothing printed
 * on it to remind them. A reference they can reach from the sofa is part of the product.
 *
 * Ordered by code length, shortest first, which is also the order worth learning: the first
 * dozen symbols cover most of what anyone types.
 */
class CodesActivity : Activity() {

    private lateinit var preferences: Preferences
    private lateinit var trees: TreeRepository
    private lateinit var table: LinearLayout
    private lateinit var switchRow: View
    private lateinit var intro: TextView
    private lateinit var languageValue: TextView
    private lateinit var summary: TextView
    private var language = Language.PL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = Preferences(this)
        trees = TreeRepository(this)
        language = preferences.activeLanguage

        languageValue = label("", ACCENT, 16f)
        summary = label("", SECONDARY, 14f)
        table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        intro = label("", SECONDARY, 15f)
        switchRow = buildSwitchRow()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(760), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(label(getString(R.string.codes_title), Color.WHITE, 28f))
            addView(intro.apply { setPadding(0, dp(8), 0, 0) })
            addView(switchRow)
            addView(summary.apply { setPadding(0, dp(14), 0, 0) })
            addView(table)
            addView(
                label(getString(R.string.codes_back), MUTED, 13f)
                    .apply { setPadding(0, dp(20), 0, 0) }
            )
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                addView(
                    LinearLayout(this@CodesActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(28), dp(28), dp(28), dp(32))
                        addView(content)
                    }
                )
            }
        )

        show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Cycles the enabled languages, so the two tables can be compared side by side in time. */
    private fun buildSwitchRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isFocusable = true
        isClickable = true
        background = card(ROW)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(18) }
        addView(
            label(getString(R.string.codes_language), Color.WHITE, 16f).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        addView(languageValue)
        setOnFocusChangeListener { view, hasFocus ->
            view.background = card(if (hasFocus) ROW_FOCUSED else ROW)
        }
        setOnClickListener {
            val enabled = preferences.enabledLanguages
            val index = enabled.indexOf(language).coerceAtLeast(0)
            language = enabled[(index + 1) % enabled.size]
            show()
        }
    }

    private fun show() {
        val set = preferences.characterSet
        val shared = preferences.treeScope == TreeScope.SHARED
        val languages = preferences.enabledLanguages
        val tree = if (shared) trees.sharedTree(languages, set) else trees.textTree(language, set)
        val weights = if (shared) {
            Weights.text(
                FrequencyTable.merge(languages.map { trees.tableFor(it) }),
                set,
                Weights.SHARED_FUNCTIONS,
            )
        } else {
            Weights.text(trees.tableFor(language), set)
        }

        // Under a shared tree there is only one table, so the switch has nothing to switch.
        switchRow.visibility = if (shared) View.GONE else View.VISIBLE
        intro.text = getString(
            if (shared) R.string.codes_intro_shared else R.string.codes_intro_per_language
        )
        languageValue.text = getString(language.titleRes)
        summary.text = getString(
            R.string.codes_summary,
            tree.symbols.size,
            tree.depth,
            tree.meanCodeLength(weights),
        )

        table.removeAllViews()
        table.addView(sectionLabel(getString(R.string.codes_first_press)))
        for (direction in Direction.entries) {
            table.addView(entry(direction.arrow, leads(tree.root.children[direction.ordinal])))
        }

        val byLength = tree.symbols
            .sortedBy { Symbol.rank(it) }
            .groupBy { requireNotNull(tree.codeOf(it)).size }
        for (length in byLength.keys.sorted()) {
            table.addView(
                sectionLabel(resources.getQuantityString(R.plurals.codes_presses, length, length))
            )
            for (chunk in byLength.getValue(length).chunked(COLUMNS)) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                for (symbol in chunk) {
                    val code = requireNotNull(tree.codeOf(symbol))
                    row.addView(cell(display(symbol), code.joinToString("") { it.arrow }))
                }
                // A short last row would otherwise stretch its cells across the width and stop
                // the columns lining up with the rows above it.
                repeat(COLUMNS - chunk.size) {
                    row.addView(
                        View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                        }
                    )
                }
                table.addView(row)
            }
        }
    }

    private fun leads(node: Node?): String = when (node) {
        null -> getString(R.string.strip_dead_branch)
        is Node.Leaf -> display(node.symbol)
        is Node.Branch -> node.preview.joinToString(" ") { display(it) } + " …"
    }

    private fun display(symbol: Symbol): String = when (symbol) {
        is Symbol.Character ->
            if (symbol.value == ' ') getString(R.string.codes_space) else symbol.value.toString()

        Symbol.Function.BACKSPACE -> getString(R.string.codes_backspace)
        Symbol.Function.CARET_LEFT -> getString(R.string.codes_caret_left)
        Symbol.Function.CARET_RIGHT -> getString(R.string.codes_caret_right)
        Symbol.Function.LANGUAGE -> getString(R.string.codes_language_symbol)
        Symbol.Function.LAYER -> getString(R.string.codes_layer)
    }

    /** One row of the four-column grid: the symbol, then the presses that produce it. */
    private fun cell(name: String, code: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), dp(12), dp(4))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(
            label(name, Color.WHITE, 15f).apply {
                layoutParams = LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
        addView(
            label(code, ACCENT, 15f).apply { typeface = Typeface.MONOSPACE }
        )
    }

    private fun entry(name: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
        addView(
            label(name, ACCENT, 16f).apply {
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
        addView(label(value, SECONDARY, 15f))
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun label(text: String, colour: Int, sizeSp: Float) = TextView(this).apply {
        this.text = text
        setTextColor(colour)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun card(colour: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10).toFloat()
        setColor(colour)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val COLUMNS = 4

        const val BACKGROUND = 0xFF08080B.toInt()
        const val ROW = 0xFF16161C.toInt()
        const val ROW_FOCUSED = 0xFF2A3A46.toInt()
        const val SECONDARY = 0xFFB0B0BC.toInt()
        const val MUTED = 0xFF6B6B78.toInt()
        const val ACCENT = 0xFF7FD1FF.toInt()
    }
}
