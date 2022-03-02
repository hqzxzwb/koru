package com.futuremind.koruksp

actual fun <T> T.freeze(): T  = this //just do nothing, freezing is Kotlin Native only