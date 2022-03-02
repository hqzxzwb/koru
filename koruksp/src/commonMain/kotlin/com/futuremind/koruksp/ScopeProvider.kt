package com.futuremind.koruksp

import kotlinx.coroutines.CoroutineScope

interface ScopeProvider {
    val scope: CoroutineScope
}