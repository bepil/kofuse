package com.github.bepil.kofuse.services.model

import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A search result for a [KtNamedFunction], which is in [function]. The [score] indicates how good of a match
 * the result is, `1.0` being a perfect match, and `0.0` being the lowest match.
 */
internal data class SearchResult(val function: KtNamedFunction, val score: Double)

/**
 * A result entry, which is in [result], where [progress] indicates the progress of results given.
 * A [progress] of `0.0` will be given for the first result, while it goes up linearly to `1.0` for the last result.
 */
internal data class ProgressResult<T>(val result: T, val progress: Double)

/**
 * Returns a new [ProgressResult] with the [ProgressResult.result] mapped using [block].
 */
internal fun <T, U> ProgressResult<T>.mapResult(block: (T) -> U) = ProgressResult(block(result), progress)
