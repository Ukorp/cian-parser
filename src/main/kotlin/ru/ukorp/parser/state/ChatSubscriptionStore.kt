package ru.ukorp.parser.state

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * chatId -> the cian.ru search URLs that chat subscribed to via the Telegram bot.
 * State is persisted/restored by StateSnapshotScheduler.
 */
@Component
class ChatSubscriptionStore {

    private val links = ConcurrentHashMap<Long, String>()

    fun add(chatId: Long, searchUrl: String) {
        links[chatId] = searchUrl
    }

    fun all(): Map<Long, String> = links

    fun restore(data: Map<Long, String>) {
        data.forEach { (chatId, url) -> links[chatId] = url }
    }
}
