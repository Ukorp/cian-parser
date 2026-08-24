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
        log.debug("Starting poll cycle for {} search URL(s)", properties.searchUrls.size)
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
            log.debug("Polling {} (attempt {}/{}) via proxy {}", searchUrl, attempt + 1, MAX_ATTEMPTS_PER_URL, proxy)
            try {
                val offers = offerFetcher.fetch(searchUrl, proxy)
                log.debug("Parsed offer ids for {}: {}", searchUrl, offers.map { it.id })
                val seen = seenOffersStore.seenIds(searchUrl)
                val newOffers = offers.filter { seen.add(it.id) }
                log.debug("New offer ids for {}: {}", searchUrl, newOffers.map { it.id })
                newOffers.forEach { offer ->
                    log.debug("Enriching offer {} with better photos", offer.id)
                    val enriched = offerFetcher.enrichWithBetterPhotos(offer, proxyPool.current())
                    log.debug("Downloading {} photo(s) for offer {}", enriched.photos.size, offer.id)
                    val photos = offerFetcher.downloadPhotos(enriched, proxyPool.current())
                    log.debug("Downloaded {} photo(s) for offer {}", photos.size, offer.id)
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
