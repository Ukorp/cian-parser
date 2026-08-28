package ru.ukorp.parser.state

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatSubscriptionStoreTest {

    @Test
    fun `add is idempotent for the same chatId and url`() {
        val store = ChatSubscriptionStore()

        store.add(1L, "https://cian.ru/a")
        store.add(1L, "https://cian.ru/a")
        store.add(1L, "https://cian.ru/b")

        assertEquals(listOf("https://cian.ru/a", "https://cian.ru/b"), store.all()[1L])
    }

    @Test
    fun `restore round-trips through all`() {
        val data = mapOf(
            1L to listOf("https://cian.ru/a", "https://cian.ru/b"),
            2L to listOf("https://cian.ru/c"),
        )

        val store = ChatSubscriptionStore()
        store.restore(data)

        assertEquals(data, store.all())
    }
}
