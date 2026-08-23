package io.github.vagrant326.atvh4.settings

import android.content.Context
import androidx.annotation.StringRes
import io.github.vagrant326.atvh4.R
import io.github.vagrant326.atvh4.core.CharacterSet
import io.github.vagrant326.atvh4.ime.CustomKeys
import io.github.vagrant326.atvh4.ime.KeyBindings
import io.github.vagrant326.atvh4.model.Language
import io.github.vagrant326.atvh4.model.TreeScope

/** How much of the next branch the strip spells out. */
enum class HintMode(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    BRANCHES(R.string.hint_branches, R.string.hint_branches_description),
    INLINE(R.string.hint_inline, R.string.hint_inline_description),
    OFF(R.string.hint_off, R.string.hint_off_description),
    ;

    fun next(): HintMode = entries[(ordinal + 1) % entries.size]
}

/**
 * A function the user can put on a button of their choosing.
 *
 * All four are conveniences, not requirements — the first three are already leaves of the code
 * tree, and the keyboard works on a remote that has nothing to spare. The trigger is the
 * exception: it has to be a real key, because the keyboard is not on screen when it is needed.
 */
enum class Binding(
    @StringRes val titleRes: Int,
    @StringRes val promptRes: Int,
    @StringRes val fallbackRes: Int,
) {
    DELETE(
        R.string.binding_delete,
        R.string.binding_delete_prompt,
        R.string.binding_delete_fallback,
    ),
    LANGUAGE(
        R.string.binding_language,
        R.string.binding_language_prompt,
        R.string.binding_language_fallback,
    ),
    LAYER(
        R.string.binding_layer,
        R.string.binding_layer_prompt,
        R.string.binding_layer_fallback,
    ),

    /**
     * The only binding the keyboard listens for while it is hidden, which is the mechanism that
     * once left a TV unnavigable — so it is one key, chosen by the user, and unassigned by
     * default. Reserved keys cannot be picked, so the d-pad is never at risk.
     */
    TRIGGER(
        R.string.binding_trigger,
        R.string.binding_trigger_prompt,
        R.string.binding_trigger_fallback,
    ),
}

class Preferences(context: Context) {

    private val store = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * Defaults to the full cross. Nothing about a Huffman code can be recalled from anywhere
     * else and the remote has nothing printed on it, so a new user has no reference at all
     * without this.
     */
    var hintMode: HintMode
        get() = store.getString(KEY_HINT_MODE, null)
            ?.let { stored -> HintMode.entries.firstOrNull { it.name == stored } }
            ?: HintMode.BRANCHES
        set(value) = store.edit().putString(KEY_HINT_MODE, value.name).apply()

    /**
     * Defaults to the full set. It is what a keyboard actually needs, and it is the
     * configuration whose published figure — 2.321 rather than 2.074 — is the honest one.
     */
    var characterSet: CharacterSet
        get() = store.getString(KEY_CHARACTER_SET, null)
            ?.let { stored -> CharacterSet.entries.firstOrNull { it.name == stored } }
            ?: CharacterSet.FULL
        set(value) = store.edit().putString(KEY_CHARACTER_SET, value.name).apply()

    fun nextCharacterSet(): CharacterSet =
        CharacterSet.entries[(characterSet.ordinal + 1) % CharacterSet.entries.size]

    /**
     * Defaults to one tree for every language. See [TreeScope] — the per-language tree is
     * better by under two per cent of a press and costs a second code table to memorise plus
     * a mode that can be in the wrong position.
     */
    var treeScope: TreeScope
        get() = store.getString(KEY_TREE_SCOPE, null)
            ?.let { stored -> TreeScope.entries.firstOrNull { it.name == stored } }
            ?: TreeScope.SHARED
        set(value) = store.edit().putString(KEY_TREE_SCOPE, value.name).apply()

    fun nextTreeScope(): TreeScope =
        TreeScope.entries[(treeScope.ordinal + 1) % TreeScope.entries.size]

    /**
     * Which languages the language switch cycles through, stored per language rather than as a
     * set of allowed combinations — the combinations grow exponentially with the number of
     * languages supported, and each one would need naming.
     *
     * Order follows [Language] declaration order so two switches always land in the same
     * place. Never empty: a keyboard with no language has no frequency table and therefore no
     * codes.
     */
    var enabledLanguages: List<Language>
        get() {
            val stored = store.getStringSet(KEY_ENABLED_LANGUAGES, null)
                ?: return listOf(Language.PL, Language.EN)
            val enabled = Language.entries.filter { it.name in stored }
            return enabled.ifEmpty { listOf(Language.entries.first()) }
        }
        set(value) {
            val kept = value.ifEmpty { listOf(Language.entries.first()) }
            store.edit().putStringSet(KEY_ENABLED_LANGUAGES, kept.map { it.name }.toSet()).apply()
            if (activeLanguage !in kept) {
                activeLanguage = kept.first()
            }
        }

    fun isEnabled(language: Language): Boolean = language in enabledLanguages

    /**
     * Toggles one language. Refuses to remove the last one and reports whether it did
     * anything, so the caller can say why nothing happened rather than looking broken.
     */
    fun toggle(language: Language): Boolean {
        val current = enabledLanguages
        if (language in current) {
            if (current.size == 1) {
                return false
            }
            enabledLanguages = current - language
        } else {
            enabledLanguages = current + language
        }
        return true
    }

    /** Optional: the language switch is also a leaf of the code tree. */
    var languageKeyCode: Int
        get() = store.getInt(KEY_LANGUAGE_KEYCODE, KeyBindings.NO_KEY)
        set(value) = store.edit().putInt(KEY_LANGUAGE_KEYCODE, value).apply()

    /**
     * Optional, and the one most worth assigning on a remote that has a spare key. Deleting is
     * two or three presses through the tree, and it is the most frequent correction on a method
     * where a wrong press produces a different character rather than a visible mismatch.
     */
    var deleteKeyCode: Int
        get() = store.getInt(KEY_DELETE_KEYCODE, KeyBindings.NO_KEY)
        set(value) = store.edit().putInt(KEY_DELETE_KEYCODE, value).apply()

    /** Unassigned by default: nothing is consumed while the keyboard is hidden until asked. */
    var triggerKeyCode: Int
        get() = store.getInt(KEY_TRIGGER_KEYCODE, KeyBindings.NO_KEY)
        set(value) = store.edit().putInt(KEY_TRIGGER_KEYCODE, value).apply()

    /** Optional: the layer switch is also a leaf of the code tree. */
    var layerKeyCode: Int
        get() = store.getInt(KEY_LAYER_KEYCODE, KeyBindings.NO_KEY)
        set(value) = store.edit().putInt(KEY_LAYER_KEYCODE, value).apply()

    val customKeys: CustomKeys
        get() = CustomKeys(languageKeyCode, deleteKeyCode, triggerKeyCode, layerKeyCode)

    fun keyCodeFor(binding: Binding): Int = when (binding) {
        Binding.LANGUAGE -> languageKeyCode
        Binding.DELETE -> deleteKeyCode
        Binding.TRIGGER -> triggerKeyCode
        Binding.LAYER -> layerKeyCode
    }

    fun assign(binding: Binding, keyCode: Int) {
        when (binding) {
            Binding.LANGUAGE -> languageKeyCode = keyCode
            Binding.DELETE -> deleteKeyCode = keyCode
            Binding.TRIGGER -> triggerKeyCode = keyCode
            Binding.LAYER -> layerKeyCode = keyCode
        }
    }

    /** Survives restarts: the language is a mode, and a mode that silently resets is a trap. */
    var activeLanguage: Language
        get() = store.getString(KEY_ACTIVE_LANGUAGE, null)
            ?.let { stored -> Language.entries.firstOrNull { it.name == stored } }
            ?.takeIf { it in enabledLanguages }
            ?: enabledLanguages.first()
        set(value) = store.edit().putString(KEY_ACTIVE_LANGUAGE, value.name).apply()

    private companion object {
        const val NAME = "h4"
        const val KEY_HINT_MODE = "hint_mode"
        const val KEY_CHARACTER_SET = "character_set"
        const val KEY_TREE_SCOPE = "tree_scope"
        const val KEY_ENABLED_LANGUAGES = "enabled_languages"
        const val KEY_ACTIVE_LANGUAGE = "active_language"
        const val KEY_LANGUAGE_KEYCODE = "language_keycode"
        const val KEY_DELETE_KEYCODE = "delete_keycode"
        const val KEY_TRIGGER_KEYCODE = "trigger_keycode"
        const val KEY_LAYER_KEYCODE = "layer_keycode"
    }
}
