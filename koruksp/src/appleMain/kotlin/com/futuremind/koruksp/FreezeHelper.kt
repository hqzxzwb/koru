package com.futuremind.koruksp

import kotlin.native.concurrent.freeze

actual fun <T> T.freeze(): T = this.freeze()