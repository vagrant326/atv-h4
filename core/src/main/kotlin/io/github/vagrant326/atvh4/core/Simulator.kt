package io.github.vagrant326.atvh4.core

data class TrialResult(
    val characters: Int,
    val codePresses: Int,
    /** Presses spent switching layer, which the character itself does not pay for. */
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
 * which is why this whole class is arithmetic where LetterWise's equivalent has to replay the
 * prediction.
 *
 * The layer round trip is charged. Without it the letters-and-space configuration would look
 * cheaper than it is by simply being unable to type an apostrophe, and every comparison
 * against the full tree would be measuring the alphabet rather than the code.
 */
class Simulator(
    private val text: CodeTree,
    private val digits: CodeTree? = null,
) {

    fun run(target: String): TrialResult {
        var codePresses = 0
        var layerPresses = 0
        var inDigitLayer = false

        for (character in target) {
            val symbol = Symbol.Character(character)
            val wantsDigitLayer = text.codeOf(symbol) == null
            if (wantsDigitLayer != inDigitLayer) {
                val from = if (inDigitLayer) digits else text
                layerPresses += requireNotNull(from?.codeOf(Symbol.Function.LAYER)) {
                    "'$character' is not in the text tree and there is no digit layer to reach it"
                }.size
                inDigitLayer = wantsDigitLayer
            }
            val tree = if (inDigitLayer) requireNotNull(digits) else text
            val code = requireNotNull(tree.codeOf(symbol)) {
                "'$character' is not typable in either tree"
            }
            codePresses += code.size
        }

        // A trailing return to the letters layer is not charged. The query is submitted from
        // wherever it ended, and the switch, if it happens at all, belongs to the next one.
        return TrialResult(target.length, codePresses, layerPresses)
    }

    fun run(targets: Iterable<String>): TrialResult =
        targets.fold(TrialResult.EMPTY) { total, target -> total + run(target) }
}
