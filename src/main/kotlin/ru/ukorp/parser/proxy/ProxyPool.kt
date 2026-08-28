package ru.ukorp.parser.proxy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.ukorp.parser.config.ParserProperties
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Round-robin pool of proxy candidates, refreshed from [ProxyProvider] lazily:
 * on first use, when every known candidate has been marked bad, or once
 * [ParserProperties.Proxy.refreshInterval] has elapsed since the last refresh.
 */
@Component
class ProxyPool(
    private val proxyProvider: ProxyProvider,
    private val properties: ParserProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = ReentrantLock()

    private var candidates: List<ProxyCandidate> = emptyList()
    private val badProxies = mutableSetOf<ProxyCandidate>()
    private var cursor = 0
    private var lastRefresh: Instant = Instant.EPOCH

    /** Returns the next healthy proxy to try, or null if none are available (caller should go direct). */
    fun current(): ProxyCandidate? = lock.withLock {
        refreshIfNeeded()
        val healthy = candidates.filterNot { it in badProxies }
        if (healthy.isEmpty()) return@withLock null
        val proxy = healthy[cursor % healthy.size]
        cursor++
        proxy
//        null
    }

    fun markBad(proxy: ProxyCandidate?) {
        if (proxy == null) return
        lock.withLock {
            if (badProxies.add(proxy)) {
                val healthyLeft = candidates.size - badProxies.size
                log.info("Marked proxy {} as bad ({} healthy of {} remaining)", proxy, healthyLeft, candidates.size)
            }
        }
    }

    private fun refreshIfNeeded() {
        val healthyCount = candidates.count { it !in badProxies }
        val stale = Duration.between(lastRefresh, Instant.now()) >= properties.proxy.refreshInterval
        if (candidates.isEmpty() || healthyCount == 0 || stale) {
            val fresh = proxyProvider.fetch()
            if (fresh.isNotEmpty()) {
                candidates = fresh
                badProxies.clear()
                cursor = 0
            }
            lastRefresh = Instant.now()
        }
    }
}
