package io.github.vagrant326.atvh4.core

data class TrialResult(
    val characters: Int,
    val codePresses: Int,
    /** Presses spent entering or leaving the digit layer, which no character pays for. */
    val layerPresses: Int,
) {
    val totalPresses: Int get() = codePresses + layerPresses

    val kspc: Double
        get() = if (characters == 0) 0.0 else totalPresses.toDouble() / characters

    operator fun plus(other: TrialResult) = TrialResult(
        characters = characters + other.characters,
        codePresses = codePresses + other.codePresses,
        layerPresses = layerPresses + other.layerPresses,
    )

    companion object {
        val EMPTY = TrialResult(0, 0, 0)
    }
}

/**
 * Exact keystroke count for entering a target string.
 *
 * Exact, not estimated: the method is deterministic, so a character costs its code length and
 * nothing else. There is no NEXT walk, no accept press and no model that could be wrong —
 * which is why this whole class is arithmetic where a predictive keyboard's equivalent has to
 * replay the prediction.
 *
 * The layer is **sticky**, and modelled as the keyboard behaves: entering costs the layer
 * code, a space inside the layer both types itself and leaves, and anything else that is not in
 * the layer leaves by `BACK` for one press. That is why a title like `blade runner 2049
 * remastered` costs one switch rather than two — the space after the digits was going to be
 * typed anyway.
 */
class Simulator(
    private val text: CodeTree,
    private val digits: CodeTree? = null,
) {

    fun run(target: String): TrialResult {
        var codePresses = 0
        var layerPresses = 0
        var inLayer = false

        for (character in target) {
            val symbol = Symbol.Character(character)
            if (inLayer) {
                val layer = requireNotNull(digits)
                val code = layer.codeOf(symbol)
                if (code != null) {
                    codePresses += code.size
                    // A space is the layer's own way out, so it is charged once, as a character.
                    if (character == ' ') {
                        inLayer = false
                    }
                    continue
                }
                // Out through BACK: one real press, and not a code.
                layerPresses += 1
                inLayer = false
            }

            val direct = text.codeOf(symbol)
            if (direct != null) {
                codePresses += direct.size
                continue
            }

            val entry = requireNotNull(text.codeOf(Symbol.Function.LAYER)) {
                "'$character' is not in the text tree and there is no layer switch to reach it"
            }
            val layer = requireNotNull(digits) {
                "'$character' is not in the text tree and there is no digit layer"
            }
            layerPresses += entry.size
            inLayer = true
            codePresses += requireNotNull(layer.codeOf(symbol)) {
                "'$character' is not typable in either tree"
            }.size
        }

        // A trailing return to the text layer is not charged. The query is submitted from
        // wherever it ended, and the switch, if it happens at all, belongs to the next one.
        return TrialResult(target.length, codePresses, layerPresses)
    }

    fun run(targets: Iterable<String>): TrialResult =
        targets.fold(TrialResult.EMPTY) { total, target -> total + run(target) }
}
