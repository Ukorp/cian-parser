package ru.ukorp.parser.state

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory only: does not survive a restart, so every offer found right after
 * a restart is treated as new again. Accepted trade-off for the MVP.
 */
@Component
class SeenOffersStore {

    private val seenByUrl = ConcurrentHashMap<String, MutableSet<String>>()

    fun seenIds(searchUrl: String): MutableSet<String> =
        seenByUrl.computeIfAbsent(searchUrl) { ConcurrentHashMap.newKeySet() }
}
