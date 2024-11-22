package org.grakovne.lissen.ui.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis

suspend fun <T> CoroutineScope.withMinimumTime(
    minimumTimeMillis: Long,
    block: suspend CoroutineScope.() -> T,
): T {
    var result: T
    val elapsedTime = measureTimeMillis {
        result = block()
    }
    val remainingTime = minimumTimeMillis - elapsedTime
    if (remainingTime > 0) {
        delay(remainingTime)
    }
    return result
}
