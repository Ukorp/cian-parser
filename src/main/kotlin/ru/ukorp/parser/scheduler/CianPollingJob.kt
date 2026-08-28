package ru.ukorp.parser.scheduler

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_ALL
import com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_NONE
import com.pengrad.telegrambot.utility.kotlin.extension.request.sendMessage
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.ukorp.parser.cian.CianBlockedException
import ru.ukorp.parser.cian.CianOfferFetcher
import ru.ukorp.parser.config.ParserProperties
import ru.ukorp.parser.proxy.ProxyPool
import ru.ukorp.parser.state.ChatSubscriptionStore
import ru.ukorp.parser.state.SeenOffersStore
import ru.ukorp.parser.telegram.TelegramNotifier
import java.net.URI
import java.net.URL

private const val MAX_ATTEMPTS_PER_URL = 10

@Component
class CianPollingJob(
    private val offerFetcher: CianOfferFetcher,
    private val seenOffersStore: SeenOffersStore,
    private val chatSubscriptionStore: ChatSubscriptionStore,
    private val telegramNotifier: TelegramNotifier,
    private val bot: TelegramBot,
) {

    @PostConstruct
    fun init() {
        bot.setUpdatesListener { updates ->
            var lastId = CONFIRMED_UPDATES_NONE
            try {
                updates.forEach { update ->
                    when(update.message().text()) {
                        "/start" -> bot.sendMessage(update.message().chat().id(), "Пришлите ссылку на циан")
                        else -> {
                            try {
                                val url = URI.create(update.message().text()).toURL()
                                if (!url.host.endsWith("cian.ru")) {
                                    bot.sendMessage(update.message().chat().id(),"Бот поддерживает только cian.ru")
                                    lastId = update.updateId()
                                    return@forEach
                                }
                            } catch (_: Exception) {
                                bot.sendMessage(update.message().chat().id(), "Введён невалидный URL!")
                                lastId = update.updateId()
                                return@forEach
                            }
                            chatSubscriptionStore.add(update.message().chat().id(), update.message().text())
                            bot.sendMessage(update.message().chat().id(), "Сканирование...")
                            pollOne(update.message().text(), update.message().chat().id())
                        }
                    }

                    lastId = update.updateId()
                }
                CONFIRMED_UPDATES_ALL
            } catch (ex: Exception) {
                log.error("Failed to process updates from Telegram: {}", ex.message, ex)
                lastId
            }
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "#{@parserProperties.pollInterval.toMillis()}")
    fun poll() {
        chatSubscriptionStore.all().entries.parallelStream().forEach { (chatId, link) ->
            if (link.isEmpty()) {
                log.debug("No search URLs configured, skipping poll cycle")
                return@forEach
            }
            log.debug("Starting poll cycle for {} search URL(s)", 1)
            pollOne(link, chatId)

        }
    }

    /**
     * Retries with a freshly rotated proxy (via [ProxyPool.markBad] + [ProxyPool.current]) up to
     * [MAX_ATTEMPTS_PER_URL] times when blocked. Deliberately a plain loop rather than Spring's
     * `@Retryable`: that annotation relies on an AOP proxy, which can't intercept a private,
     * self-invoked method call like `forEach(::pollOne)` from within this same class.
     */
    private fun pollOne(searchUrl: String, chatId: Long) {
        repeat(MAX_ATTEMPTS_PER_URL) { attempt ->
            log.debug("Polling {} (attempt {}/{})", searchUrl, attempt + 1, MAX_ATTEMPTS_PER_URL)
            try {
                val offers = offerFetcher.fetch(searchUrl)
                log.debug("Parsed offer ids for {}: {}", searchUrl, offers.map { it.id })
                val seen = seenOffersStore.seenIds(chatId, searchUrl)
                val newOffers = offers.filter { seen.add(it.id) }
                log.debug("New offer ids for {}: {}", searchUrl, newOffers.map { it.id })
                newOffers.forEach { offer ->
                    log.debug("Enriching offer {} with better photos", offer.id)
                    val enriched = offerFetcher.enrichWithBetterPhotos(offer)
                    log.debug("Downloading {} photo(s) for offer {}", enriched.photos.size, offer.id)
                    val photos = offerFetcher.downloadPhotos(enriched)
                    log.debug("Downloaded {} photo(s) for offer {}", photos.size, offer.id)
                    telegramNotifier.notifyNewOffer(enriched, photos, chatId)
                }
                log.info("Polled {} ({} offers, {} new)", searchUrl, offers.size, newOffers.size)
                return
            } catch (ex: CianBlockedException) {
                log.warn(
                    "Blocked while polling {} (attempt {}/{}): {}",
                    searchUrl, attempt + 1, MAX_ATTEMPTS_PER_URL, ex.message,
                )
            } catch (ex: Exception) {
                log.error("Failed to poll {}", searchUrl, ex)
                return
            }
        }
        log.warn("Giving up on {} after {} blocked attempts", searchUrl, MAX_ATTEMPTS_PER_URL)
    }
}
