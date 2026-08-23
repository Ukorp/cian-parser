package ru.ukorp.parser.scheduler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.ukorp.parser.cian.CianBlockedException
import ru.ukorp.parser.cian.CianOfferFetcher
import ru.ukorp.parser.config.ParserProperties
import ru.ukorp.parser.proxy.ProxyPool
import ru.ukorp.parser.state.SeenOffersStore
import ru.ukorp.parser.telegram.TelegramNotifier

private const val MAX_ATTEMPTS_PER_URL = 10

@Component
class CianPollingJob(
    private val properties: ParserProperties,
    private val offerFetcher: CianOfferFetcher,
    private val proxyPool: ProxyPool,
    private val seenOffersStore: SeenOffersStore,
    private val telegramNotifier: TelegramNotifier,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "#{@parserProperties.pollInterval.toMillis()}")
    fun poll() {
        if (properties.searchUrls.isEmpty()) {
            log.debug("No search URLs configured, skipping poll cycle")
            return
        }
        properties.searchUrls.forEach(::pollOne)
    }

    /**
     * Retries with a freshly rotated proxy (via [ProxyPool.markBad] + [ProxyPool.current]) up to
     * [MAX_ATTEMPTS_PER_URL] times when blocked. Deliberately a plain loop rather than Spring's
     * `@Retryable`: that annotation relies on an AOP proxy, which can't intercept a private,
     * self-invoked method call like `forEach(::pollOne)` from within this same class.
     */
    private fun pollOne(searchUrl: String) {
        repeat(MAX_ATTEMPTS_PER_URL) { attempt ->
            val proxy = proxyPool.current()
            try {
                val offers = offerFetcher.fetch(searchUrl, proxy)
                val seen = seenOffersStore.seenIds(searchUrl)
                val newOffers = offers.filter { seen.add(it.id) }
                newOffers.forEach { offer ->
                    val enriched = offerFetcher.enrichWithBetterPhotos(offer, proxyPool.current())
                    val photos = offerFetcher.downloadPhotos(enriched, proxyPool.current())
                    telegramNotifier.notifyNewOffer(enriched, photos)
                }
                log.info("Polled {} ({} offers, {} new)", searchUrl, offers.size, newOffers.size)
                return
            } catch (ex: CianBlockedException) {
                proxyPool.markBad(proxy)
                log.warn(
                    "Blocked while polling {} via proxy {} (attempt {}/{}): {}",
                    searchUrl, proxy, attempt + 1, MAX_ATTEMPTS_PER_URL, ex.message,
                )
            } catch (ex: Exception) {
                log.error("Failed to poll {}", searchUrl, ex)
                return
            }
        }
        log.warn("Giving up on {} after {} blocked attempts", searchUrl, MAX_ATTEMPTS_PER_URL)
    }
}
