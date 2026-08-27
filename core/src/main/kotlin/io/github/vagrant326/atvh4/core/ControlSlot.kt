package io.github.vagrant326.atvh4.core

/**
 * One position in the reserved branch: either something that happens, or four more positions.
 *
 * The branch started as four leaves and ran out. Space, delete, the layer and the edit mode
 * filled it exactly, and the case switch and the mark layer are two more functions with the
 * same problem as the first three — no corpus records how often anybody presses them, so
 * Huffman cannot place them from anything real. Structure is the answer that was already being
 * used; this only lets it go one level deeper.
 *
 * One level, and no more. A second would be a fourth press for something the user is supposed
 * to be able to find without reading, and at that depth the branch stops being a list of
 * positions and becomes a tree to memorise — which is the thing the reserved branch exists to
 * avoid.
 */
sealed interface ControlSlot {

    /** Two presses: the reserved direction, then this one. */
    data class Leaf(val symbol: Symbol) : ControlSlot

    /**
     * Three presses for anything inside. Fewer than four entries is allowed; the unused
     * directions lead nowhere, exactly as they do at the top level.
     */
    data class Branch(val slots: Map<Direction, Symbol>) : ControlSlot

    val symbols: Collection<Symbol>
        get() = when (this) {
            is Leaf -> listOf(symbol)
            is Branch -> slots.values
        }
}
