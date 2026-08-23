package ru.ukorp.parser.telegram

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InputMediaPhoto
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMediaGroup
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPhoto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.ukorp.parser.cian.CianOffer
import ru.ukorp.parser.config.ParserProperties

/**
 * Bot token / chat id come from config (see application.yaml).
 */
@Component
class TelegramNotifier(
    private val properties: ParserProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val bot = TelegramBot(properties.telegram.botToken)

    /**
     * [photos] are raw image bytes, not URLs: images.cdn-cian.ru is hotlink-protected and rejects
     * Telegram's own server-side fetch, so photos must be uploaded directly (see
     * CianOfferFetcher.downloadPhotos) rather than referenced by URL.
     */
    fun notifyNewOffer(offer: CianOffer, photos: List<ByteArray>) {
        val chatId = properties.telegram.chatId
        val caption = buildMessage(offer)

        val response = when {
            photos.size >= 2 -> bot.execute(SendMediaGroup(chatId, *mediaGroup(photos, caption)))
            photos.size == 1 -> bot.execute(
                SendPhoto(chatId, photos.first()).caption(caption).parseMode(ParseMode.HTML)
            )
            else -> bot.execute(SendMessage(chatId, caption).parseMode(ParseMode.HTML))
        }

        if (response.isOk) {
            log.info("Notified Telegram about new offer {}", offer.id)
        } else {
            log.warn(
                "Telegram rejected notification for offer {}: {} {}",
                offer.id, response.errorCode(), response.description(),
            )
        }
    }

    private fun mediaGroup(photos: List<ByteArray>, caption: String): Array<InputMediaPhoto> =
        photos.mapIndexed { index, bytes ->
            val photo = InputMediaPhoto(bytes)
            if (index == 0) photo.caption(caption).parseMode(ParseMode.HTML) else photo
        }.toTypedArray()

    private fun buildMessage(offer: CianOffer): String = buildString {
        appendLine("<b>${escape(offer.title.ifBlank { "Новое объявление" })}</b>")
        if (offer.subtitle.isNotBlank()) appendLine(escape(offer.subtitle))
        if (offer.price.isNotBlank()) appendLine(escape(offer.price))
        if (!offer.metroStation.isNullOrBlank()) {
            val remoteness = offer.metroRemoteness?.let { ", ${escape(it)}" }.orEmpty()
            appendLine("🚇 ${escape(offer.metroStation)}$remoteness")
        }
        if (offer.description.isNotBlank()) appendLine(escape(offer.description))
        append(offer.url)
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
