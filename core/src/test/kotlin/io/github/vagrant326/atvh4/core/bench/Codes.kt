package io.github.vagrant326.atvh4.core.bench

import io.github.vagrant326.atvh4.core.CodeTree
import io.github.vagrant326.atvh4.core.Direction
import io.github.vagrant326.atvh4.core.FrequencyTable
import io.github.vagrant326.atvh4.core.Node
import io.github.vagrant326.atvh4.core.Symbol
import io.github.vagrant326.atvh4.core.Weights
import java.io.File

/**
 * Prints the code table.
 *
 * In every other method in the programme this would be a debugging aid. Here the tree *is*
 * the interface — it is the thing the user memorises, and a TV remote has nothing printed on
 * it to help — so being able to read the table out is how the README, the settings screen and
 * any printed cheat sheet stay true to what the keyboard actually does.
 *
 * Run with `./gradlew :core:codes -Planguage=pl -Pset=full`.
 */
fun main(arguments: Array<String>) {
    val options = arguments.toList().chunked(2)
        .filter { it.size == 2 }
        .associate { it[0].removePrefix("--") to it[1] }

    val path = options["table"] ?: "app/src/main/assets/frequencies-pl.bin"
    val file = File(path)
    if (!file.isFile) {
        System.err.println("no frequency table at $path; run corpus/count.py first")
        return
    }
    val table = file.inputStream().use { FrequencyTable.read(it) }
    val weights = Weights.text(table)
    val tree = CodeTree.withControlBranch(weights, Weights.CONTROL_BRANCH)

    println()
    println("$path: ${tree.symbols.size} symbols, deepest ${tree.depth}")
    println("expected %.3f presses per character".format(tree.meanCodeLength(weights)))
    println()

    println("first press")
    for (direction in Direction.entries) {
        val child = tree.root.children[direction.ordinal]
        val leads = when (child) {
            null -> "—"
            is Node.Leaf -> child.symbol.label
            // What one more press finishes, then how much is further down. The full table follows.
        is Node.Branch -> {
            val immediate = child.immediate.joinToString(" ") { it.label }
            if (child.deeper.isEmpty()) immediate else "$immediate  · ${child.deeper.size} deeper"
        }
        }
        println("  ${direction.arrow}  $leads")
    }
    println()

    val ordered = tree.symbols.sortedWith(
        compareBy({ tree.codeOf(it)?.size ?: 0 }, { Symbol.rank(it) })
    )
    var length = 0
    for (symbol in ordered) {
        val code = requireNotNull(tree.codeOf(symbol))
        if (code.size != length) {
            length = code.size
            println("$length ${if (length == 1) "press" else "presses"}")
        }
        println("  %-6s %s".format(symbol.label, code.joinToString("") { it.arrow }))
    }
    println()
}
