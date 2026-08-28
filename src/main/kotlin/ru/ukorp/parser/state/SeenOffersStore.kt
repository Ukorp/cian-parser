package ru.ukorp.parser.state

import org.springframework.stereotype.Component
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val MAX_SEEN_PER_KEY = 28

/**
 * Bounded to the last [MAX_SEEN_PER_KEY] offer ids per (chatId, searchUrl): that's how many
 * offers cian.ru returns on a single search page, so anything older has already scrolled off
 * and is no longer relevant for dedup. State is persisted/restored by StateSnapshotScheduler.
 */
@Component
class SeenOffersStore {

    private val seenByKey = ConcurrentHashMap<Pair<Long, String>, MutableSet<String>>()

    fun seenIds(chatId: Long, searchUrl: String): MutableSet<String> =
        seenByKey.computeIfAbsent(chatId to searchUrl) { mutableSetOf() }

    fun snapshot(): Map<Pair<Long, String>, List<String>> =
        seenByKey.mapValues { (_, ids) -> synchronized(ids) { ids.toList() } }

    fun restore(data: Map<Pair<Long, String>, List<String>>) {
        data.forEach { (key, ids) ->
            val set = mutableSetOf<String>()
            set.addAll(ids)
            seenByKey[key] = set
        }
    }
}
