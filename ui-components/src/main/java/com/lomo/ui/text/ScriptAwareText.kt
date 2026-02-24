package com.lomo.ui.text

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

fun CharSequence.isCjkDominant(): Boolean {
    var cjkCount = 0
    var letterDigitCount = 0

    forEach { char ->
        if (char.isWhitespace()) return@forEach
        if (char.isCjkScript()) {
            cjkCount++
        } else if (char.isLetterOrDigit()) {
            letterDigitCount++
        }
    }

    if (cjkCount == 0) return false
    if (letterDigitCount == 0) return true
    return cjkCount >= letterDigitCount
}

fun CharSequence.scriptAwareTextAlign(): TextAlign = if (isCjkDominant()) TextAlign.Justify else TextAlign.Start

fun TextStyle.scriptAwareFor(text: CharSequence): TextStyle {
    val cjkDominant = text.isCjkDominant()
    return copy(
        // M3 default letter spacing is tuned for latin scripts and looks loose in CJK paragraphs.
        letterSpacing = if (cjkDominant) 0.sp else letterSpacing,
        lineBreak = LineBreak.Paragraph,
        hyphens = if (cjkDominant) Hyphens.None else Hyphens.Auto,
    )
}

private fun Char.isCjkScript(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}
