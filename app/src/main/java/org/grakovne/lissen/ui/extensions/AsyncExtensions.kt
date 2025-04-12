package org.grakovne.lissen.ui.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

suspend fun <T> withMinimumTime(
    minimumTimeMillis: Long,
    block: suspend CoroutineScope.() -> T,
): T {
    var result: T
    val elapsedTime = measureTimeMillis {
        result = coroutineScope { block() }
    }
    val remainingTime = minimumTimeMillis - elapsedTime
    if (remainingTime > 0) {
        delay(remainingTime)
    }
    return result
}

fun CoroutineScope.setupWaitingIcon(
    checkCondition: () -> Boolean,
    onLongWaiting: () -> Unit,
    onWaitingFinished: () -> Unit,
    waitingTime: Long = 300,
) {
    if (checkCondition()) {
        onWaitingFinished()
        return
    }

    launch {
        delay(waitingTime)

        if (!checkCondition()) {
            onLongWaiting()
        }
    }
}
