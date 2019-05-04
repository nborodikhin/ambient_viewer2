package com.pinnacleimagingsystems.ambientviewer2

import java.util.concurrent.atomic.AtomicBoolean

class ConsumableEvent<T>(private val data: T, createConsumed: Boolean = false) {
    private var consumed = AtomicBoolean(createConsumed)

    fun get() = data

    fun consume(consumer: (T) -> Unit) {
        if (consumed.getAndSet(true)) {
            return
        }

        consumer(data)
    }
}