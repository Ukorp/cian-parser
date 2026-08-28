package ru.ukorp.parser.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeenOffersStoreTest {

    @Test
    fun `evicts the oldest id once a key exceeds 28 entries`() {
        val store = SeenOffersStore()
        val seen = store.seenIds(1L, "https://cian.ru/a")

        (1..29).forEach { seen.add("offer-$it") }

        assertEquals(28, seen.size)
        assertFalse("offer-1" in seen)
        assertTrue("offer-29" in seen)
    }

    @Test
    fun `different chatId-searchUrl keys do not evict each other`() {
        val store = SeenOffersStore()
        val seenA = store.seenIds(1L, "https://cian.ru/a")
        val seenB = store.seenIds(1L, "https://cian.ru/b")

        (1..28).forEach { seenA.add("offer-$it") }
        seenB.add("offer-x")

        assertEquals(28, seenA.size)
        assertTrue("offer-x" in seenB)
        assertFalse("offer-x" in seenA)
    }

    @Test
    fun `snapshot and restore round-trip preserves order and cap`() {
        val store = SeenOffersStore()
        val key = 1L to "https://cian.ru/a"
        store.seenIds(key.first, key.second).apply { (1..28).forEach { add("offer-$it") } }

        val snapshot = store.snapshot()

        val restored = SeenOffersStore()
        restored.restore(snapshot)
        val restoredSeen = restored.seenIds(key.first, key.second)

        assertEquals((1..28).map { "offer-$it" }, restoredSeen.toList())

        restoredSeen.add("offer-29")
        assertEquals(28, restoredSeen.size)
        assertFalse("offer-1" in restoredSeen)
    }
}
