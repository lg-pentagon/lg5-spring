package com.lg5.spring.outbox

interface OutboxScheduler {
    fun processOutboxMessage()
}