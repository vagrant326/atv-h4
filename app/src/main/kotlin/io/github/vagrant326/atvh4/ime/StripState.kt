package io.github.vagrant326.atvh4.ime

import io.github.vagrant326.atvh4.core.Coder
import io.github.vagrant326.atvh4.model.Language
import io.github.vagrant326.atvh4.model.Mode
import io.github.vagrant326.atvh4.model.TreeScope
import io.github.vagrant326.atvh4.settings.HintMode

/** Everything the strip draws, so adding a mode does not mean adding another parameter. */
data class StripState(
    val coder: Coder,
    val language: Language,
    val enabledLanguages: List<Language>,
    /**
     * Whether one tree covers every language or each has its own. It decides what the tag says,
     * which matters: under a shared tree there is no language mode to be in the wrong position,
     * and under per-language trees there is nothing more important for the strip to report.
     */
    val scope: TreeScope,
    /** False when a frequency table failed to load, which makes every code arbitrary. */
    val trained: Boolean,
    val hintMode: HintMode,
    val showLanguageChooser: Boolean,
    val customKeys: CustomKeys,
    /**
     * Whether there is anywhere to send characters. True whenever a field opened the keyboard;
     * false when the trigger key raised it over an app that never asked for input, where the
     * keys arrive but there is no connection to write through.
     */
    val hasEditor: Boolean,
    /**
     * Which of the three meanings the four directions have. [Mode.EDIT] is the one that is not
     * a code tree, so the guide draws a fixed legend for it rather than branches.
     */
    val mode: Mode,
    /**
     * Whether the last press went into unused code space. Shown rather than swallowed: on a
     * method where a press is normally invisible until the code completes, silence is
     * indistinguishable from the remote not being heard.
     */
    val deadPress: Boolean,
)
