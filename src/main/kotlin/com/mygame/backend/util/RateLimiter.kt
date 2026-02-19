package com.mygame.backend.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class RateLimiter(private val limit: Int, private val timeWindowMs: Long) {
    private val requests = ConcurrentHashMap<String, MutableList<Long>>()

    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = requests.computeIfAbsent(key) { java.util.Collections.synchronizedList(mutableListOf()) }
        
        synchronized(timestamps) {
            timestamps.removeIf { it < now - timeWindowMs }
            if (timestamps.size < limit) {
                timestamps.add(now)
                return true
            }
            return false
        }
    }
}
