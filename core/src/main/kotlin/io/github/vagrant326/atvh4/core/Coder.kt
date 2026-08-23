package io.github.vagrant326.atvh4.core

sealed interface Press {
    /** A code completed. Exactly one symbol, no alternatives, nothing to confirm. */
    data class Emitted(val symbol: Symbol) : Press

    /** Still inside the tree. */
    data object Descended : Press

    /**
     * That direction leads nowhere from here. Unused code space is unavoidable — Huffman
     * pads the leaf count up to a whole number of merges — so a dead branch is a normal
     * state, not an error, and the strip shows it as empty rather than the IME swallowing
     * the press silently.
     */
    data object Dead : Press
}

/**
 * Where in the tree the user currently is, kept out of the IME service so the walk can be
 * tested without a device.
 *
 * It owns no text. The editor holds the text and the caret, and a copy here would be a second
 * source of truth that goes stale the moment anything else touches the field. What it owns is
 * the partial code, which nothing else can know about.
 */
class Coder(tree: CodeTree) {

    var tree: CodeTree = tree
        private set

    private val trail = ArrayList<Direction>(MAX_TRAIL)
    private var current: Node.Branch = tree.root

    /** The presses made so far towards the current symbol. */
    val path: List<Direction> get() = trail

    val hasPartialCode: Boolean get() = trail.isNotEmpty()

    /** What each direction leads to from here, in [Direction] order. Null where nothing does. */
    val branches: List<Node?> get() = current.children

    fun press(direction: Direction): Press {
        val next = current.children[direction.ordinal] ?: return Press.Dead
        return when (next) {
            is Node.Leaf -> {
                reset()
                Press.Emitted(next.symbol)
            }

            is Node.Branch -> {
                trail += direction
                current = next
                Press.Descended
            }
        }
    }

    /** Abandons the partial code. Returns whether there was one, so the caller can say so. */
    fun abandon(): Boolean {
        val had = trail.isNotEmpty()
        reset()
        return had
    }

    /**
     * Swaps the tree — a language change, a layer change, a different character set.
     *
     * The partial code is dropped rather than replayed. Half a code means nothing in another
     * tree: the same three presses lead somewhere entirely unrelated, so replaying them would
     * produce a character the user never aimed at, which is precisely the mode error this
     * method is most exposed to.
     */
    fun use(replacement: CodeTree) {
        tree = replacement
        reset()
    }

    private fun reset() {
        trail.clear()
        current = tree.root
    }

    private companion object {
        /** Codes never get near this; it only spares the list a growth step. */
        const val MAX_TRAIL = 8
    }
}
