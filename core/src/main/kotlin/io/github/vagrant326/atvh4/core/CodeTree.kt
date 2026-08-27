package io.github.vagrant326.atvh4.core

import java.util.PriorityQueue

/**
 * The four code symbols. Declaration order is the order the strip draws them and the order
 * the canonical code assignment counts in, so it is part of the format: change it and every
 * code changes with it.
 */
enum class Direction(val arrow: String) {
    UP("↑"),
    LEFT("←"),
    RIGHT("→"),
    DOWN("↓"),
}

sealed interface Node {

    val weight: Long

    /**
     * Every symbol under this node, in [Symbol.rank] order — not a sample of them.
     *
     * This is what makes the guide usable on the first attempt: a branch says *what is down
     * there* rather than only that something is. It is the whole set because a capped list is
     * worse than useless for the one question being asked — is my letter in here — since the
     * answer "not in the twelve I showed you" is not an answer. How to arrange it for reading
     * is the view's problem; rank order here only makes the list deterministic.
     */
    val beneath: List<Symbol>

    data class Leaf(val symbol: Symbol, override val weight: Long) : Node {
        override val beneath: List<Symbol> get() = listOf(symbol)
    }

    class Branch(
        /** Always [ARITY] entries. Null where the code space is unused. */
        val children: List<Node?>,
        override val weight: Long,
        override val beneath: List<Symbol>,
    ) : Node {

        /**
         * Symbols one press away: the children that are already leaves.
         *
         * Split from [deeper] because the two answer different questions. "One more press and
         * you have it" is a decision the user can act on now; "somewhere under here" only tells
         * them which way to go. Presenting both as one list is what makes a wide branch read as
         * a junk drawer.
         */
        val immediate: List<Symbol>
            get() = children.filterIsInstance<Leaf>()
                .map { it.symbol }
                .sortedBy { Symbol.rank(it) }

        /** Symbols more than one press away. */
        val deeper: List<Symbol> get() = beneath - immediate.toSet()
    }
}

/** Number of code symbols. Four, because that is what a d-pad has. */
const val ARITY = 4

/**
 * Which of the equal-length codes a symbol receives. Huffman fixes the code *lengths*, so
 * this choice cannot change KSPC by a single press — it only decides which arrangement of
 * the same-cost codes the user has to memorise.
 */
enum class Ordering {
    /**
     * Pinned by [Symbol.rank], so the assignment never moves for a reason that costs nothing.
     * This is what keeps two languages' trees as close as their length distributions allow,
     * and it is the shipped default.
     */
    PINNED,

    /**
     * Heaviest symbol takes the earliest code. The obvious choice, kept so the bench can
     * report what pinning actually buys — the two figures differ in agreement between the
     * languages and in nothing else.
     */
    FREQUENCY,
}

/**
 * A base-4 Huffman code over a symbol frequency distribution: the method, in one class.
 *
 * Frequent symbols get one- or two-press codes, rare ones four or five, and a completed code
 * emits exactly one symbol — there is no ambiguity, no prediction and nothing to confirm.
 * Which means the frequency distribution *is* the interface, and a wrong frequency table does
 * not degrade the keyboard, it produces a different one.
 *
 * Code lengths come from Huffman, so they are optimal for the distribution they were built
 * from. Which of the equal-length codes each symbol receives is then decided by
 * [Symbol.rank] rather than by frequency — see the comment there for why that choice is
 * worth its own rule.
 */
class CodeTree private constructor(
    val root: Node.Branch,
    private val byCode: Map<Symbol, List<Direction>>,
) {

    val symbols: List<Symbol> get() = byCode.keys.toList()

    fun codeOf(symbol: Symbol): List<Direction>? = byCode[symbol]

    /** Longest code in the tree, which is the worst a single character can cost. */
    val depth: Int get() = byCode.values.maxOf { it.size }

    /** Expected presses per symbol under [weights]: the KSPC this tree implies. */
    fun meanCodeLength(weights: Map<Symbol, Long>): Double {
        var presses = 0.0
        var total = 0.0
        for ((symbol, code) in byCode) {
            val weight = weights[symbol] ?: continue
            presses += code.size.toDouble() * weight
            total += weight.toDouble()
        }
        return if (total == 0.0) 0.0 else presses / total
    }

    /**
     * How many of [other]'s codes are identical here. The cost of running one tree per
     * language is motor memory, so this is the number that says how much of it was avoided.
     */
    fun agreementWith(other: CodeTree): Double {
        val shared = byCode.keys.intersect(other.byCode.keys)
        if (shared.isEmpty()) {
            return 0.0
        }
        val same = shared.count { byCode[it] == other.byCode[it] }
        return same.toDouble() / shared.size
    }

    companion object {

        /**
         * @param weights every symbol the tree must be able to produce, with its frequency.
         *   Zero-weight symbols are kept — a symbol that cannot be typed at all is worse than
         *   one that costs five presses — and end up at the bottom of the tree.
         */
        fun of(weights: Map<Symbol, Long>, ordering: Ordering = Ordering.PINNED): CodeTree {
            require(weights.size >= 2) { "a code tree needs at least two symbols" }
            val lengths = codeLengths(weights)
            val assigned = assign(lengths, weights, ordering)
            return CodeTree(build(assigned, weights), assigned)
        }

        /**
         * One direction reserved for a fixed set of functions, the other three carrying the
         * characters.
         *
         * The reason this exists is not comfort, it is honesty. Delete, the digit layer and the
         * language switch **have no frequency in any corpus** — no text records how often
         * somebody corrects a typo or enters a PIN — so letting Huffman place them means letting
         * it place them from numbers that were invented. Reserving a branch replaces three
         * fabricated weights with one structural decision, and gives all of them the same
         * two-press cost, in the same place, permanently.
         *
         * The bill is paid by the letters: three root branches instead of four is a quarter less
         * room at every depth. [Benchmark] prices it.
         *
         * @param characters everything that is not a function, with its measured frequency.
         * @param control what each direction under [reserved] does. Fewer than [ARITY] entries
         *   is allowed; the unused directions simply lead nowhere.
         */
        fun withControlBranch(
            characters: Map<Symbol, Long>,
            control: Map<Direction, ControlSlot>,
            reserved: Direction = Direction.UP,
            ordering: Ordering = Ordering.PINNED,
        ): CodeTree {
            require(characters.size >= 2) { "a code tree needs at least two characters" }
            require(control.isNotEmpty()) { "a reserved branch with nothing in it wastes a press" }

            val codes = LinkedHashMap<Symbol, List<Direction>>()
            for ((direction, slot) in control) {
                when (slot) {
                    is ControlSlot.Leaf -> codes[slot.symbol] = listOf(reserved, direction)

                    is ControlSlot.Branch ->
                        for ((inner, symbol) in slot.slots) {
                            codes[symbol] = listOf(reserved, direction, inner)
                        }
                }
            }

            val arrange = arrangement(ordering)
            val forest = forest(characters, roots = ARITY - 1).sortedWith(arrange)
            val carriers = Direction.entries.filter { it != reserved }
            for ((at, tree) in forest.withIndex()) {
                collect(tree, listOf(carriers[at]), codes, arrange)
            }

            val weights = characters +
                control.values.flatMap { it.symbols }.associateWith { CONTROL_PREVIEW_WEIGHT }
            return CodeTree(build(codes, weights), codes)
        }

        /**
         * How a node's children are laid across the four directions. Never changes a code
         * *length*, so it cannot change KSPC — see [Ordering].
         *
         * Padding sorts last under both, which is what keeps a dead slot at the end of a branch
         * rather than punching a hole in the middle of one.
         */
        private fun arrangement(ordering: Ordering): Comparator<Merge> = when (ordering) {
            Ordering.PINNED -> compareBy { lightestRank(it) }
            Ordering.FREQUENCY -> compareByDescending<Merge> { it.weight }
                .thenBy { lightestRank(it) }
        }

        /**
         * Nominal, and only ever used to order the preview on the strip. A function's real
         * weight is the whole point of [withControlBranch]: it does not have one.
         */
        private const val CONTROL_PREVIEW_WEIGHT = 1L

        /**
         * Huffman at arity four. The queue is ordered by weight and then by a tie-breaking
         * sequence number, because the two languages must produce the same tree from the same
         * table on every machine — a hash iteration order leaking into the code assignment
         * would be invisible in tests and wrong on the device.
         */
        /**
         * Huffman, stopped at [roots] trees rather than at one.
         *
         * Stopping early is what lets a direction be reserved: three trees become the three
         * carrying branches, and the fourth root slot is spoken for elsewhere. The queue is
         * ordered by weight and then by a tie-breaking sequence number, because the same table
         * must produce the same tree on every machine — a hash iteration order leaking into the
         * assignment would be invisible in tests and wrong on the device.
         */
        private fun forest(weights: Map<Symbol, Long>, roots: Int): List<Merge> {
            require(weights.size >= roots) { "${weights.size} symbols cannot fill $roots roots" }
            var sequence = 0
            val queue = PriorityQueue<Merge>(compareBy({ it.weight }, { it.sequence }))

            // Each merge consumes exactly ARITY nodes and yields one, so the leaf count has to
            // reach `roots` with no remainder. Without the padding the shortfall lands on the
            // *first* merge, which is the one holding the rarest symbols - and one of them
            // silently gets promoted to a shorter code than the distribution earns.
            val step = ARITY - 1
            val padding = ((roots - weights.size) % step + step) % step
            repeat(padding) { queue += Merge(0L, sequence++, null, emptyList()) }

            for (symbol in weights.keys.sortedBy { Symbol.rank(it) }) {
                queue += Merge(weights.getValue(symbol), sequence++, symbol, emptyList())
            }

            while (queue.size > roots) {
                val group = List(ARITY) { queue.poll() }
                queue += Merge(group.sumOf { it.weight }, sequence++, null, group)
            }
            return List(queue.size) { queue.poll() }
        }

        private fun codeLengths(weights: Map<Symbol, Long>): Map<Symbol, Int> {
            val lengths = HashMap<Symbol, Int>(weights.size)
            fun walk(node: Merge, depth: Int) {
                val symbol = node.leaf
                if (symbol != null) {
                    lengths[symbol] = depth
                    return
                }
                for (child in node.children) {
                    walk(child, depth + 1)
                }
            }
            walk(forest(weights, roots = 1).single(), 0)
            return lengths
        }

        /** Reads codes straight off a merge tree, arranging each node's children in place. */
        private fun collect(
            node: Merge,
            prefix: List<Direction>,
            into: MutableMap<Symbol, List<Direction>>,
            arrange: Comparator<Merge>,
        ) {
            val symbol = node.leaf
            if (symbol != null) {
                into[symbol] = prefix
                return
            }
            for ((at, child) in node.children.sortedWith(arrange).withIndex()) {
                if (child.leaf == null && child.children.isEmpty()) {
                    continue
                }
                collect(child, prefix + Direction.entries[at], into, arrange)
            }
        }

        private fun lightestRank(node: Merge): Int {
            val symbol = node.leaf
            return when {
                symbol != null -> Symbol.rank(symbol)
                node.children.isEmpty() -> Int.MAX_VALUE
                else -> node.children.minOf { lightestRank(it) }
            }
        }

        /**
         * Canonical assignment: symbols sorted by code length and then by [Ordering], each
         * taking the next code of its length. Prefix-freeness comes out of the ordering rather
         * than being checked afterwards.
         */
        private fun assign(
            lengths: Map<Symbol, Int>,
            weights: Map<Symbol, Long>,
            ordering: Ordering,
        ): Map<Symbol, List<Direction>> {
            val within = when (ordering) {
                Ordering.PINNED -> compareBy<Map.Entry<Symbol, Int>> { Symbol.rank(it.key) }
                Ordering.FREQUENCY -> compareByDescending<Map.Entry<Symbol, Int>> {
                    weights[it.key] ?: 0L
                }.thenBy { Symbol.rank(it.key) }
            }
            val byLength = compareBy<Map.Entry<Symbol, Int>> { it.value }
            val ordered = lengths.entries.sortedWith(byLength.then(within))
            val codes = LinkedHashMap<Symbol, List<Direction>>(lengths.size)
            var code = 0L
            var previous = ordered.first().value
            for ((symbol, length) in ordered) {
                code = code shl (2 * (length - previous))
                codes[symbol] = digits(code, length)
                code++
                previous = length
            }
            return codes
        }

        /** Base-4, most significant digit first: the presses in the order they are made. */
        private fun digits(code: Long, length: Int): List<Direction> =
            List(length) { at ->
                val shift = 2 * (length - 1 - at)
                Direction.entries[((code shr shift) and 0b11L).toInt()]
            }

        private fun build(
            codes: Map<Symbol, List<Direction>>,
            weights: Map<Symbol, Long>,
        ): Node.Branch {
            val root = Slot()
            for ((symbol, code) in codes) {
                var slot = root
                for (step in code) {
                    slot = slot.children[step.ordinal] ?: Slot().also {
                        slot.children[step.ordinal] = it
                    }
                }
                check(slot.symbol == null) { "'${symbol.label}' collides with an existing code" }
                slot.symbol = symbol
            }
            return freeze(root, weights) as Node.Branch
        }

        private fun freeze(slot: Slot, weights: Map<Symbol, Long>): Node {
            val symbol = slot.symbol
            if (symbol != null) {
                check(slot.children.all { it == null }) {
                    "'${symbol.label}' sits above another code, so the code is not prefix-free"
                }
                return Node.Leaf(symbol, weights[symbol] ?: 0L)
            }
            val children = slot.children.map { child -> child?.let { freeze(it, weights) } }
            val beneath = children.filterNotNull()
                .flatMap { it.beneath }
                .sortedBy { Symbol.rank(it) }
            return Node.Branch(children, children.filterNotNull().sumOf { it.weight }, beneath)
        }

        private class Merge(
            val weight: Long,
            val sequence: Int,
            val leaf: Symbol?,
            val children: List<Merge>,
        )

        private class Slot {
            var symbol: Symbol? = null
            val children = arrayOfNulls<Slot>(ARITY)
        }
    }
}
