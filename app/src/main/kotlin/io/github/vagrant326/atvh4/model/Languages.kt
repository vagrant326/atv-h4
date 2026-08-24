package io.github.vagrant326.atvh4.model

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import io.github.vagrant326.atvh4.R
import io.github.vagrant326.atvh4.core.CharacterSet
import io.github.vagrant326.atvh4.core.CodeTree
import io.github.vagrant326.atvh4.core.FrequencyTable
import io.github.vagrant326.atvh4.core.Symbol
import io.github.vagrant326.atvh4.core.Weights

/**
 * [label] is the two-letter tag shown on the strip, where space is scarce; [titleRes] is the
 * language's own name, for the settings list. Names of languages are not translated — Polski
 * is Polski in every locale.
 *
 * Adding a language is one entry here plus one asset: `frequencies-<code>.bin`, built by
 * `corpus/count.py`. Nothing else in the app knows how many languages there are, and there is
 * no per-language layout, alphabet or rule to write — the frequency table carries all of it,
 * because under a Huffman code the frequency table *is* the layout.
 */
enum class Language(
    val code: String,
    val label: String,
    @StringRes val titleRes: Int,
) {
    PL("pl", "PL", R.string.language_pl),
    EN("en", "EN", R.string.language_en),
}

/** Which tree the keyboard is currently walking. */
enum class Layer {
    TEXT,
    DIGITS,
}

/**
 * Whether the enabled languages share one tree or each get their own.
 *
 * [PER_LANGUAGE] is the default because it wins on presses: measured on 2000 held-out titles
 * per language it costs about 1% less than one merged tree, in both languages.
 *
 * It is worth recording why the other answer looked right first. The case for a merged tree was
 * that two trees mean two tables to memorise and a language mode that can sit in the wrong
 * position, where a wrong-language press emits a valid character and the mistake reads as a
 * typo. Both of those are arguments about *memorised* typing. **This keyboard does not assume
 * muscle memory** — the branch guide is treated as always-on, and a user reading the guide
 * cannot make a mode error, because the guide shows the branches of the tree actually in force.
 * So neither argument survives, and the decision falls to presses alone.
 *
 * [SHARED] stays for anyone who does stop reading the guide, where the reasoning above starts
 * to apply again.
 */
enum class TreeScope {
    PER_LANGUAGE,
    SHARED,
}

/**
 * Loads a language's frequency table, builds its code tree, and keeps both.
 *
 * The tree is built on the device rather than shipped ready-made. It is a few dozen nodes, so
 * the saving would be nothing, and building it here from the same Kotlin the simulator calls
 * is what stops the measured method and the typed method from being two different methods.
 *
 * A missing or unreadable asset falls back to a uniform table over a plain ASCII alphabet.
 * That still types — every symbol keeps a code — but the codes are worthless, the whole point
 * of the method being the frequency ordering, so the strip says so rather than hiding it.
 * Polish diacritics are absent in that state, which is another reason to make it visible.
 */
class TreeRepository(private val context: Context) {

    private val tables = HashMap<Language, FrequencyTable>()
    private val trees = HashMap<Key, CodeTree>()

    /** One tree for the digit layer whatever the language: ten digits look the same. */
    val digitTree: CodeTree by lazy { CodeTree.of(Weights.digitLayer()) }

    fun textTree(language: Language, set: CharacterSet): CodeTree =
        trees.getOrPut(Key(listOf(language), set, shared = false)) {
            CodeTree.of(Weights.text(tableFor(language), set))
        }

    /**
     * One tree over every enabled language's counts summed. Adding a language therefore
     * changes the codes, which is the honest consequence of one table covering them all and
     * the reason the setting carries a warning.
     */
    fun sharedTree(languages: List<Language>, set: CharacterSet): CodeTree =
        trees.getOrPut(Key(languages, set, shared = true)) {
            val merged = FrequencyTable.merge(languages.map { tableFor(it) })
            CodeTree.of(Weights.text(merged, set, Weights.SHARED_FUNCTIONS))
        }

    fun isTrained(language: Language): Boolean = tableFor(language) !== FALLBACK

    fun tableFor(language: Language): FrequencyTable = tables.getOrPut(language) {
        val name = "frequencies-${language.code}.bin"
        runCatching {
            context.assets.open(name).use { FrequencyTable.read(it) }
        }.getOrElse { failure ->
            Log.w(TAG, "no usable frequency table in $name, falling back to uniform", failure)
            FALLBACK
        }
    }

    // The scope belongs in the key: one enabled language makes the two trees agree on their
    // language list while still differing, because the shared tree drops the language switch.
    private data class Key(
        val languages: List<Language>,
        val set: CharacterSet,
        val shared: Boolean,
    )

    private companion object {
        const val TAG = "H4"

        /**
         * Uniform over ASCII. Not a language and not meant to be — it exists so a broken
         * install is a keyboard with bad codes rather than a keyboard with no codes.
         */
        val FALLBACK: FrequencyTable = FrequencyTable(
            buildMap {
                put(' ', 1L)
                for (letter in 'a'..'z') {
                    put(letter, 1L)
                }
                for (character in Symbol.DIGITS + Symbol.PUNCTUATION) {
                    put(character, 1L)
                }
            }
        )
    }
}
