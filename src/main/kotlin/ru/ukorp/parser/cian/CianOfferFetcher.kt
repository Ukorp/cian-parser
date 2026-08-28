package ru.ukorp.parser.cian

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import ru.ukorp.parser.proxy.ProxyCandidate
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val REFERER = "https://www.cian.ru/"

@Component
class CianOfferFetcher(
    private val htmlParser: CianHtmlParser,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable("offers")
    fun fetch(searchUrl: String, proxy: ProxyCandidate? = null): List<CianOffer> {
        val html = fetchHtml(searchUrl, proxy)
        val document = Jsoup.parse(html, searchUrl)
        val offers = htmlParser.parse(document)
        log.debug("Fetched {} ({} bytes) -> {} offer(s)", searchUrl, html.length, offers.size)
        return offers
    }

    /**
     * Best-effort: visits the offer's own page to pull higher-resolution photos than the search
     * card exposes (see CianImageExtractor). Only called for offers we're about to notify about
     * (i.e. genuinely new ones), so the extra request per poll cycle stays bounded. Any failure
     * (blocked/timeout/parse miss) just falls back to the photos already found on the search card.
     */
    fun enrichWithBetterPhotos(offer: CianOffer, proxy: ProxyCandidate? = null): CianOffer {
        val html = try {
            fetchHtml(offer.url, proxy)
        } catch (ex: Exception) {
            log.debug("Could not fetch offer page {} for photo enrichment: {}", offer.url, ex.message)
            return offer
        }
        val betterPhotos = CianImageExtractor.extractBestImages(html, limit = 5)
        log.debug("Enrichment for offer {} found {} candidate photo(s)", offer.id, betterPhotos.size)
        return if (betterPhotos.isNotEmpty()) offer.copy(photos = betterPhotos) else offer
    }

    /**
     * Downloads the offer's photos ourselves instead of handing Telegram the bare CDN URLs:
     * images.cdn-cian.ru is hotlink-protected and only serves an image when the request carries a
     * matching Referer. Telegram's own server-side fetcher doesn't send one, so passing it a raw
     * URL gets the whole notification rejected with "WEBPAGE_MEDIA_EMPTY". Fetching the bytes here
     * (with the same headers that already work for the HTML requests) and uploading them directly
     * sidesteps that entirely. Photos that fail to download are silently skipped.
     */
    fun downloadPhotos(offer: CianOffer, proxy: ProxyCandidate? = null): List<ByteArray> =
        offer.photos.take(5).mapNotNull { url -> downloadImage(url, proxy) }

    private fun downloadImage(url: String, proxy: ProxyCandidate?): ByteArray? {
        log.debug("Downloading photo {} via proxy {}", url, proxy)
        return try {
            val bytes = restClientFor(proxy).get()
                .uri(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .retrieve()
                .body(ByteArray::class.java)
            log.debug("Downloaded photo {} ({} bytes)", url, bytes?.size ?: 0)
            bytes
        } catch (ex: Exception) {
            log.debug("Failed to download photo {}: {}", url, ex.message)
            null
        }
    }

    private fun fetchHtml(url: String, proxy: ProxyCandidate?): String {
        log.debug("Requesting {} via proxy {}", url, proxy)
        return try {
            val body = restClientFor(proxy).get()
                .uri(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .retrieve()
                .body(String::class.java)
                ?: throw CianBlockedException("Empty response body from $url via proxy $proxy")
            log.debug("Received {} bytes from {}", body.length, url)
            body
        } catch (ex: CianBlockedException) {
            throw ex
        } catch (ex: Exception) {
            throw CianBlockedException("Request to $url via proxy $proxy failed: ${ex.message}", ex)
        }
    }

    private fun restClientFor(proxy: ProxyCandidate?): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofSeconds(15))
            if (proxy != null) {
                setProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port)))
            }
        }
        return RestClient.builder().requestFactory(requestFactory).build()
    }
}
