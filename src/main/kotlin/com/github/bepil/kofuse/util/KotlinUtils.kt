package com.github.bepil.kofuse.util

import arrow.core.Either
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Returns [this], ensuring it is [cancel]ed when [disposable] is disposed.
 */
fun CoroutineScope.disposing(disposable: Disposable): CoroutineScope = this.also {
    Disposer.register(disposable) {
        it.cancel()
    }
}

/**
 * Awaits the first result from [this] and returns it. This method blocks while it waits.
 */
suspend fun <T> Flow<T>.awaitFirst(): T = coroutineScope {
    val result = CompletableDeferred<T>()
    val job = launch {
        this@awaitFirst.collect {
            result.complete(it)
        }
    }
    result.await().also { job.cancel() }
}

/**
 * As [mapValues], but [transform] also gets an integer, starting from 0 and increasing by one with each subsequent
 * invocation.
 */
fun <K, V, O> Map<K, V>.mapEntriesIndexed(transform: (Int, Map.Entry<K, V>) -> O): Map<K, O> {
    var i = 0
    return mapValues {
        transform(i, it).also {
            i++
        }
    }
}

/**
 * Like [merge], however, it is possible to merge two [Flow]s of different types.
 */
fun <X, Y> mergeEither(left: Flow<X>, right: Flow<Y>) =
    merge(left.map { Either.Left(it) }, right.map { Either.Right(it) })
