package com.futuremind.koruksp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*


class FlowWrapper<T>(
  private val scopeProvider: ScopeProvider?,
  private val freezeWrapper: Boolean,
  private val flow: Flow<T>
) {

    init {
        if (freezeWrapper) this.freeze()
    }

    fun subscribe(
        onEach: (item: T) -> Unit,
        onComplete: () -> Unit,
        onThrow: (error: Throwable) -> Unit
    ) = subscribe(
        scope = scopeProvider?.scope
            ?: throw IllegalArgumentException("To use implicit scope, you have to provide it via @ToNativeClass.launchOnScope and @ExportedScopeProvider."),
        onEach = onEach,
        onComplete = onComplete,
        onThrow = onThrow
    )

    fun subscribe(
        scope: CoroutineScope,
        onEach: (item: T) -> Unit,
        onComplete: () -> Unit,
        onThrow: (error: Throwable) -> Unit
    ): Job = flow
        .onEach { onEach(it.freeze()) }
        .catch { onThrow(it.freeze()) }
        .onCompletion { onComplete() }
        .launchIn(scope)
        .apply { if (freezeWrapper) this.freeze() }
}