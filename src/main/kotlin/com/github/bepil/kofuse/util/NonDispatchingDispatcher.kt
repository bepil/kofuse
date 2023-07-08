package com.github.bepil.kofuse.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] that does not dispatch, i.e.: it directly runs
 * [Runnable]s offered to it.
 */

class NonDispatchingDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = false
}
