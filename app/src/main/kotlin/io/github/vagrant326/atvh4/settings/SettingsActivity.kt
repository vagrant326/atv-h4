package io.github.vagrant326.atvh4.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.vagrant326.atvh4.BuildConfig
import io.github.vagrant326.atvh4.R
import io.github.vagrant326.atvh4.ime.KeyBindings
import io.github.vagrant326.atvh4.model.Language
import io.github.vagrant326.atvh4.model.TreeScope
import io.github.vagrant326.atvh4.update.UpdateActivity

/**
 * The app's only settings screen. It exists because an Android TV app with no launcher activity
 * is invisible on the home screen, and because `method.xml` has to point `settingsActivity`
 * somewhere.
 *
 * This app is installed on its own and knows nothing about the other keyboards in the
 * programme. Comparing methods happens by switching IME in the system settings, not here.
 */
class SettingsActivity : Activity() {

    private lateinit var preferences: Preferences

    /**
     * The binding rows are the only ones whose value is changed somewhere else. Everything else
     * on this screen changes in place and updates its own text in the click handler.
     */
    private val bindingLabels = mutableMapOf<Binding, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = Preferences(this)

        // Rows are capped rather than stretched across the panel. A full-width control on a
        // TV is a metre of switch for two words of label.
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(680), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        content.addView(heading(getString(R.string.ime_name)))
        content.addView(caption(getString(R.string.settings_version, BuildConfig.VERSION_NAME)))
        // Named here rather than on the support page. The repository is not an ask for money,
        // and it is not tied to where updates come from either — those may leave GitHub for a
        // store, while the source stays where it is.
        content.addView(
            caption(getString(R.string.settings_source, getString(R.string.settings_source_url)))
        )

        content.addView(sectionLabel(getString(R.string.settings_section_keyboard)))
        content.addView(hintModeRow())
        content.addView(treeScopeRow())

        content.addView(sectionLabel(getString(R.string.settings_section_codes)))
        content.addView(
            navigationRow(getString(R.string.settings_codes)) {
                startActivity(Intent(this, CodesActivity::class.java))
            }
        )
        content.addView(caption(getString(R.string.settings_codes_note)))

        content.addView(sectionLabel(getString(R.string.settings_section_languages)))
        content.addView(languageRows())

        content.addView(sectionLabel(getString(R.string.settings_section_buttons)))
        for (binding in Binding.entries) {
            content.addView(captureRow(binding))
        }
        content.addView(caption(getString(R.string.settings_buttons_note)))

        content.addView(sectionLabel(getString(R.string.settings_section_system)))
        content.addView(
            navigationRow(getString(R.string.settings_system_keyboard)) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )
        content.addView(caption(getString(R.string.settings_system_keyboard_note)))

        content.addView(sectionLabel(getString(R.string.settings_section_updates)))
        content.addView(
            row(getString(R.string.settings_check_updates), "") {
                startActivity(Intent(this, UpdateActivity::class.java))
            }
        )
        content.addView(caption(getString(R.string.settings_updates_note)))

        content.addView(sectionLabel(getString(R.string.settings_section_support)))
        content.addView(
            row(getString(R.string.settings_support), "") {
                startActivity(Intent(this, SupportActivity::class.java))
            }
        )

        content.addView(sectionLabel(getString(R.string.settings_section_typing)))
        content.addView(caption(getString(R.string.settings_typing_note)))

        content.addView(sectionLabel(getString(R.string.settings_section_try)))
        content.addView(scratchField())
        content.addView(caption(getString(R.string.settings_scratch_note)))

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                addView(
                    LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(28), dp(24), dp(28), dp(32))
                        addView(content)
                    }
                )
            }
        )
    }

    /**
     * `KeyCaptureActivity` writes the new code straight to preferences and finishes, so the
     * labels built in [onCreate] are stale on the way back and stayed stale until the process
     * died — which read as a change that had not been saved, when it always had been.
     *
     * Only the labels are re-read. Rebuilding the content view here would work too and would
     * drop d-pad focus on every return, which on a TV is worse than what it fixes.
     */
    override fun onResume() {
        super.onResume()
        for ((binding, label) in bindingLabels) {
            label.text = bindingLabel(binding)
        }
    }

    /**
     * Somewhere to try the keyboard without leaving the app and without editing anything real.
     * Not persisted and not read by anything.
     *
     * The explicit focus flags and `showSoftInput` are not decoration: on a TV there is no
     * touch, so a field that does not take d-pad focus cannot be reached at all, and focusing
     * one does not always raise the IME on its own.
     */
    private fun scratchField() = EditText(this).apply {
        hint = getString(R.string.settings_scratch_hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setTextColor(Color.WHITE)
        setHintTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(FIELD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }

        setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundColor(if (hasFocus) FIELD_FOCUSED else FIELD)
            if (hasFocus) {
                showKeyboardFor(view)
            }
        }
        setOnClickListener { showKeyboardFor(it) }
    }

    private fun showKeyboardFor(view: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hintModeRow(): View {
        lateinit var value: TextView
        lateinit var explain: TextView

        val control = row(
            getString(R.string.settings_key_hint),
            getString(preferences.hintMode.labelRes),
        ) {
            preferences.hintMode = preferences.hintMode.next()
            value.text = getString(preferences.hintMode.labelRes)
            explain.text = getString(preferences.hintMode.descriptionRes)
        }
        value = control.getChildAt(1) as TextView
        explain = caption(getString(preferences.hintMode.descriptionRes))

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(control)
            addView(explain)
        }
    }

    /**
     * One tree for every language or one each. The measured difference is under two per cent
     * of a press, so the description talks about the thing that is actually at stake — how many
     * code tables the user has to hold in their thumb.
     */
    private fun treeScopeRow(): View {
        lateinit var value: TextView
        lateinit var explain: TextView

        val control = row(
            getString(R.string.settings_tree_scope),
            getString(labelOf(preferences.treeScope)),
        ) {
            preferences.treeScope = preferences.nextTreeScope()
            value.text = getString(labelOf(preferences.treeScope))
            explain.text = getString(descriptionOf(preferences.treeScope))
        }
        value = control.getChildAt(1) as TextView
        explain = caption(getString(descriptionOf(preferences.treeScope)))

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(control)
            addView(explain)
            addView(caption(getString(R.string.settings_codes_change_warning)))
        }
    }

    private fun labelOf(scope: TreeScope) = when (scope) {
        TreeScope.SHARED -> R.string.scope_shared
        TreeScope.PER_LANGUAGE -> R.string.scope_per_language
    }

    private fun descriptionOf(scope: TreeScope) = when (scope) {
        TreeScope.SHARED -> R.string.scope_shared_description
        TreeScope.PER_LANGUAGE -> R.string.scope_per_language_description
    }

    /**
     * One row per language rather than a list of allowed combinations. Combinations grow
     * exponentially with the number of languages and each one would need a name; checkboxes
     * grow by one row.
     */
    private fun languageRows(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val note = caption(getString(R.string.settings_language_note))

        for (language in Language.entries) {
            lateinit var mark: TextView

            val control = row(
                getString(language.titleRes),
                checkbox(preferences.isEnabled(language)),
            ) {
                if (preferences.toggle(language)) {
                    mark.text = checkbox(preferences.isEnabled(language))
                    note.text = getString(R.string.settings_language_note)
                } else {
                    note.text = getString(R.string.settings_language_minimum)
                }
            }
            mark = control.getChildAt(1) as TextView
            container.addView(control)
        }
        container.addView(note)
        return container
    }

    private fun checkbox(checked: Boolean) = if (checked) "☑" else "☐"

    private fun captureRow(binding: Binding): View {
        val row = navigationRow(bindingLabel(binding)) {
            startActivity(
                Intent(this, KeyCaptureActivity::class.java)
                    .putExtra(KeyCaptureActivity.EXTRA_BINDING, binding.name)
            )
        }
        bindingLabels[binding] = row.getChildAt(0) as TextView
        return row
    }

    private fun bindingLabel(binding: Binding): String {
        val code = preferences.keyCodeFor(binding)
        val value = if (code == KeyBindings.NO_KEY) {
            getString(R.string.settings_binding_unset)
        } else {
            KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        }
        return getString(R.string.settings_binding_row, getString(binding.titleRes), value)
    }

    /**
     * Visually distinct from the setting rows above, because it does something categorically
     * different: it leaves the screen. A row that cycles a value in place and a row that
     * throws you into Android settings should not look the same.
     */
    private fun navigationRow(label: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isFocusable = true
        isClickable = true
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setBackgroundColor(NAV_ROW)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }

        addView(
            TextView(this@SettingsActivity).apply {
                text = label
                setTextColor(SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        addView(
            TextView(this@SettingsActivity).apply {
                text = "↗"
                setTextColor(SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            }
        )

        setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundColor(if (hasFocus) NAV_ROW_FOCUSED else NAV_ROW)
        }
        setOnClickListener { onClick() }
    }

    /** Label on the left, current value on the right, focus visible. Standard TV list row. */
    private fun row(label: String, value: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isFocusable = true
        isClickable = true
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setBackgroundColor(ROW)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }

        addView(
            TextView(this@SettingsActivity).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        addView(
            TextView(this@SettingsActivity).apply {
                text = value
                setTextColor(ACCENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
        )

        setOnFocusChangeListener { view, hasFocus ->
            view.setBackgroundColor(if (hasFocus) ROW_FOCUSED else ROW)
        }
        setOnClickListener { onClick() }
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, dp(22), 0, dp(2))
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(SECONDARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(6), 0, 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xFF08080B.toInt()
        const val ROW = 0xFF16161C.toInt()
        const val ROW_FOCUSED = 0xFF2A3A46.toInt()
        // Flatter and dimmer than a setting row: it is a way out, not a value to change.
        const val NAV_ROW = 0xFF101014.toInt()
        const val NAV_ROW_FOCUSED = 0xFF232430.toInt()
        const val FIELD = 0xFF16161C.toInt()
        const val FIELD_FOCUSED = 0xFF22303A.toInt()
        const val SECONDARY = 0xFFB0B0BC.toInt()
        const val MUTED = 0xFF6B6B78.toInt()
        const val ACCENT = 0xFF7FD1FF.toInt()
    }
}
