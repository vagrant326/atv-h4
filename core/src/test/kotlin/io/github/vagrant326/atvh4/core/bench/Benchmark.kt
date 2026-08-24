package io.github.vagrant326.atvh4.core.bench

import io.github.vagrant326.atvh4.core.CharacterSet
import io.github.vagrant326.atvh4.core.CodeTree
import io.github.vagrant326.atvh4.core.FrequencyTable
import io.github.vagrant326.atvh4.core.Ordering
import io.github.vagrant326.atvh4.core.Simulator
import io.github.vagrant326.atvh4.core.Symbol
import io.github.vagrant326.atvh4.core.TrialResult
import io.github.vagrant326.atvh4.core.Weights
import java.io.File

/**
 * Measures KSPC over a query corpus, using the same tree construction the keyboard runs.
 *
 * There is no model column and nothing to compare against a uniform baseline: the method is
 * deterministic, so the only things that can differ are the frequency table the tree was built
 * from and which characters it carries. That is what this reports — one tree per language
 * against a single merged tree, and letters-with-punctuation against letters alone.
 *
 * The pinned code assignment does not appear as a KSPC row, because it cannot change one: it
 * reorders codes within a length class and leaves every length alone. What it changes is the
 * agreement figure at the bottom. Reporting it as a KSPC row would suggest a trade that is not
 * there.
 *
 * Lives in the test source set so it stays out of the APK. Run with `./gradlew :core:bench`.
 */
private data class Query(val text: String, val language: String)

private class Report(val label: String) {
    var full: TrialResult = TrialResult.EMPTY
    var letters: TrialResult = TrialResult.EMPTY
    var merged: TrialResult = TrialResult.EMPTY
    val worst = ArrayList<Pair<String, Double>>()
}

private fun textTree(table: FrequencyTable, set: CharacterSet, ordering: Ordering = Ordering.PINNED) =
    CodeTree.withControlBranch(Weights.text(table, set), Weights.CONTROL_BRANCH, ordering = ordering)

fun main(arguments: Array<String>) {
    val options = arguments.toList().chunked(2)
        .filter { it.size == 2 }
        .associate { it[0].removePrefix("--") to it[1] }

    val queryFile = File(options["queries"] ?: "bench/queries-v1.tsv")
    val queries = read(queryFile)
    if (queries.isEmpty()) {
        System.err.println("no queries in ${queryFile.path}")
        return
    }

    val tables = listOf("pl", "en").mapNotNull { language ->
        load(options["table-$language"])?.let { language to it }
    }.toMap()
    if (tables.size < 2) {
        System.err.println("both frequency tables are needed; run corpus/count.py first")
        return
    }

    val digits = CodeTree.withControlBranch(Weights.digitLayer(), Weights.DIGIT_CONTROL_BRANCH)
    val mergedTable = FrequencyTable.merge(tables.values)
    val mergedTree = textTree(mergedTable, CharacterSet.FULL)
    val trees = tables.mapValues { (_, table) -> textTree(table, CharacterSet.FULL) }
    val letterTrees = tables.mapValues { (_, table) -> textTree(table, CharacterSet.LETTERS) }

    val reports = linkedMapOf("pl" to Report("Polish"), "en" to Report("English"))

    var untypable = 0
    for (query in queries) {
        // A query carrying Polish letters is typed on the Polish tree whatever the row says,
        // and "piątek the series" is exactly that case.
        val language = if (query.text.any { it !in EN_TYPABLE }) "pl" else query.language
        val target = if (language == "pl") "pl" else "en"
        val report = reports.getValue(target)

        val outcome = runCatching {
            Triple(
                Simulator(trees.getValue(target), digits).run(query.text),
                Simulator(letterTrees.getValue(target), digits).run(query.text),
                Simulator(mergedTree, digits).run(query.text),
            )
        }
        if (outcome.isFailure) {
            System.err.println("untypable: ${query.text}  (${outcome.exceptionOrNull()?.message})")
            untypable++
            continue
        }
        val (full, letters, merged) = outcome.getOrThrow()
        report.full += full
        report.letters += letters
        report.merged += merged
        report.worst += query.text to full.kspc
    }

    println()
    println("queries ${queries.size}, untypable $untypable, corpus ${queryFile.path}")
    println()
    println("%-10s %8s %8s %8s %8s".format("set", "chars", "full", "letters", "merged"))
    var full = TrialResult.EMPTY
    var letters = TrialResult.EMPTY
    var merged = TrialResult.EMPTY
    for (report in reports.values) {
        if (report.full.characters == 0) {
            continue
        }
        full += report.full
        letters += report.letters
        merged += report.merged
        line(report.label, report.full, report.letters, report.merged)
    }
    if (full.characters > 0) {
        line("all", full, letters, merged)
    }

    println()
    println("the tree, per language")
    for ((language, table) in tables) {
        val tree = trees.getValue(language)
        val weights = Weights.text(table, CharacterSet.FULL)
        println(
            "  %-4s %.3f presses per character, deepest %d, %d characters within two presses"
                .format(
                    language,
                    tree.meanCodeLength(weights),
                    tree.depth,
                    tree.symbols.count { requireNotNull(tree.codeOf(it)).size <= 2 },
                )
        )
    }

    // What the pinned assignment buys. It cannot change a code length, so it never appears in
    // the KSPC table above.
    val pinned = trees.getValue("pl").agreementWith(trees.getValue("en"))
    val loose = textTree(tables.getValue("pl"), CharacterSet.FULL, Ordering.FREQUENCY)
        .agreementWith(textTree(tables.getValue("en"), CharacterSet.FULL, Ordering.FREQUENCY))
    println()
    println("shared codes identical between the Polish and English trees")
    println("  pinned by rank    %.0f%%".format(pinned * 100))
    println("  ordered by weight %.0f%%".format(loose * 100))

    println()
    println("most expensive queries")
    reports.values.flatMap { it.worst }
        .sortedByDescending { it.second }
        .take(5)
        .forEach { (text, kspc) -> println("  %.3f  %s".format(kspc, text)) }
    println()
}

private fun line(label: String, full: TrialResult, letters: TrialResult, merged: TrialResult) {
    println(
        "%-10s %8d %8.3f %8.3f %8.3f".format(
            label,
            full.characters,
            full.kspc,
            letters.kspc,
            merged.kspc,
        )
    )
}

private fun load(path: String?): FrequencyTable? {
    val file = File(path ?: return null)
    if (!file.isFile) {
        System.err.println("no frequency table at $path")
        return null
    }
    return file.inputStream().use { FrequencyTable.read(it) }
}

private val EN_TYPABLE: Set<Char> =
    (" abcdefghijklmnopqrstuvwxyz" + Symbol.DIGITS.joinToString("") +
        Symbol.PUNCTUATION.joinToString("")).toSet()

private fun read(file: File): List<Query> {
    if (!file.isFile) {
        System.err.println("no query file at ${file.path}")
        return emptyList()
    }
    return file.readLines()
        .asSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .drop(1) // header
        .map { it.split('\t') }
        .filter { it.size >= 2 }
        .map { Query(it[0].trim(), it[1].trim()) }
        .filter { it.text.isNotEmpty() }
        .toList()
}
