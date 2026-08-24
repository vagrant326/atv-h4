plugins {
    alias(libs.plugins.kotlin.jvm)
}

// No Android dependencies here, ever. The simulator and the shipped IME build the code tree
// with the same code, otherwise measured KSPC and typed KSPC diverge silently — and in a
// deterministic method that divergence would be a different keyboard, not a worse estimate.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

/**
 * KSPC over the query corpus, using the shipped tree construction. Lives in the test source
 * set so the runner never reaches the APK.
 *
 * Reports the three tree configurations docs/20-h4writer.md §3 asks to be measured against
 * each other: one tree per language, one merged tree for both, and the per-language trees
 * with code assignment pinned so the two languages agree wherever the code lengths allow.
 */
tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Measures KSPC over bench/queries-v1.tsv"
    mainClass.set("io.github.vagrant326.atvh4.core.bench.BenchmarkKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
    args(
        "--queries", "bench/queries-v1.tsv",
        "--table-pl", "app/src/main/assets/frequencies-pl.bin",
        "--table-en", "app/src/main/assets/frequencies-en.bin",
    )
}

/**
 * Prints the code table a user would have to learn, per language and per character set.
 * The tree *is* the interface here, so being able to read it out is not a debugging
 * convenience — it is how the README and the printable cheat sheet stay true.
 */
tasks.register<JavaExec>("codes") {
    group = "documentation"
    description = "Prints the code table. -Planguage=pl, -Ptable=<path>."
    mainClass.set("io.github.vagrant326.atvh4.core.bench.CodesKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
    val language = providers.gradleProperty("language").orElse("pl")
    // -Ptable points the task at a table that is not the shipped one, which is how a candidate
    // domain mix gets read out without copying files over the committed assets. Note that
    // `--args` cannot do this job: Gradle appends argumentProviders *after* args, so the
    // default below would win and the override would look like it had been ignored.
    val table = providers.gradleProperty("table")
    argumentProviders.add {
        listOf(
            "--table",
            table.orNull ?: "app/src/main/assets/frequencies-${language.get()}.bin",
        )
    }
}
