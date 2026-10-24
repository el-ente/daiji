package io.github.elente.daiji.data

import java.text.Normalizer
import kotlin.math.abs

private const val SUBSTRING = 300
private const val WORD_PREFIXES = 200
private const val ONE_TYPO = 150
private const val LETTERS_IN_ORDER = 100
private const val MIN_TYPO_LENGTH = 4

/**
 * Fuzzy title search for remote typing: the query as a substring, each word as the start of a title word
 * ("sup met"), one typo or swapped pair ("metriod"), or its letters in order ("mtrd"). Best matches first;
 * a blank query keeps the list as it is.
 */
fun <T> List<T>.matchingTitle(query: String, title: (T) -> String): List<T> {
    val needle = normalize(query)
    if (needle.isEmpty()) return this
    return mapNotNull { item -> score(normalize(title(item)), needle)?.let { it to item } }
        .sortedByDescending { it.first }
        .map { it.second }
}

/** Lowercase, accents and punctuation dropped, so "Pokémon: Red" is "pokemon red". */
private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .filter { it.isLetterOrDigit() || it == ' ' }
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")

private fun score(title: String, needle: String): Int? {
    val index = title.indexOf(needle)
    if (index >= 0) return SUBSTRING - index
    val words = title.split(' ')
    if (needle.split(' ').all { token -> words.any { it.startsWith(token) } }) return WORD_PREFIXES
    val compactTitle = title.replace(" ", "")
    val compactNeedle = needle.replace(" ", "")
    if (compactNeedle.length >= MIN_TYPO_LENGTH && hasWindowWithinOneEdit(compactTitle, compactNeedle)) return ONE_TYPO
    if (isSubsequence(compactNeedle, compactTitle)) return LETTERS_IN_ORDER
    return null
}

private fun hasWindowWithinOneEdit(text: String, needle: String): Boolean =
    (needle.length - 1..needle.length + 1).any { size ->
        size in 1..text.length && text.windowed(size).any { withinOneEdit(it, needle) }
    }

/** Damerau-Levenshtein distance of at most one: a letter added, dropped, replaced, or two swapped. */
private fun withinOneEdit(a: String, b: String): Boolean {
    if (abs(a.length - b.length) > 1) return false
    val (short, long) = if (a.length <= b.length) a to b else b to a
    var i = 0
    var j = 0
    var edits = 0
    while (i < short.length && j < long.length) {
        if (short[i] == long[j]) {
            i++
            j++
            continue
        }
        if (++edits > 1) return false
        when {
            short.length != long.length -> j++
            isSwap(short, i, long, j) -> { i += 2; j += 2 }
            else -> { i++; j++ }
        }
    }
    return edits + (short.length - i) + (long.length - j) <= 1
}

private fun isSwap(a: String, i: Int, b: String, j: Int): Boolean =
    i + 1 < a.length && j + 1 < b.length && a[i] == b[j + 1] && a[i + 1] == b[j]

private fun isSubsequence(needle: String, text: String): Boolean {
    var next = 0
    for (char in text) {
        if (char == needle[next] && ++next == needle.length) return true
    }
    return false
}
