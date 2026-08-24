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
     * The heaviest symbols under this node, heaviest first. This is what makes the guide
     * usable on the first attempt: a branch can say *what is down there* rather than only
     * that something is.
     */
    val preview: List<Symbol>

    data class Leaf(val symbol: Symbol, override val weight: Long) : Node {
        override val preview: List<Symbol> get() = listOf(symbol)
    }

    class Branch(
        /** Always [ARITY] entries. Null where the code space is unused. */
        val children: List<Node?>,
        override val weight: Long,
        override val preview: List<Symbol>,
    ) : Node
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
         * How many symbols a branch names on the strip before it gives up and truncates.
         *
         * Sized so the *first* press is never a guess. With the shipped tables the root's four
         * branches hold about a dozen symbols each, and a truncated list is the one failure the
         * guide must not have: a user who cannot see their letter has to pick a direction at
         * random and walk back. Deeper branches are far smaller, so this bound only ever binds
         * at the top.
         */
        const val PREVIEW_LIMIT = 12

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
         * Huffman at arity four. The queue is ordered by weight and then by a tie-breaking
         * sequence number, because the two languages must produce the same tree from the same
         * table on every machine — a hash iteration order leaking into the code assignment
         * would be invisible in tests and wrong on the device.
         */
        private fun codeLengths(weights: Map<Symbol, Long>): Map<Symbol, Int> {
            var sequence = 0
            val queue = PriorityQueue<Merge>(compareBy({ it.weight }, { it.sequence }))

            // Each merge consumes exactly ARITY nodes, so the leaf count has to leave no
            // remainder. Without the padding the shortfall lands on the *first* merge, which
            // is the one holding the rarest symbols - and one of them silently gets promoted
            // to a shorter code than the distribution earns.
            val padding = (ARITY - 1 - (weights.size - 1) % (ARITY - 1)) % (ARITY - 1)
            repeat(padding) { queue += Merge(0L, sequence++, null, emptyList()) }

            for (symbol in weights.keys.sortedBy { Symbol.rank(it) }) {
                queue += Merge(weights.getValue(symbol), sequence++, symbol, emptyList())
            }

            while (queue.size > 1) {
                val group = List(ARITY) { queue.poll() }
                queue += Merge(group.sumOf { it.weight }, sequence++, null, group)
            }

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
            walk(queue.poll(), 0)
            return lengths
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
            val preview = children.filterNotNull()
                .flatMap { it.preview }
                .sortedByDescending { weights[it] ?: 0L }
                .take(PREVIEW_LIMIT)
            return Node.Branch(children, children.filterNotNull().sumOf { it.weight }, preview)
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
