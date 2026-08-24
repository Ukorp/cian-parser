package ru.ukorp.parser.proxy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import ru.ukorp.parser.config.ParserProperties

/**
 * Fetches a fresh plain-text `ip:port` proxy list from ProxyScrape
 * (https://api.proxyscrape.com/v4/free-proxy-list/get?...), no API key required.
 */
@Component
class ProxyProvider(
    private val properties: ParserProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    fun fetch(): List<ProxyCandidate> {
        log.debug("Fetching proxy list from {}", properties.proxy.sourceUrl)
        val body = try {
            restClient.get().uri(properties.proxy.sourceUrl).retrieve().body(String::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to fetch proxy list from {}: {}", properties.proxy.sourceUrl, ex.message)
            null
        } ?: return emptyList()

        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val proxies = lines.mapNotNull { parseLine(it) }
        log.debug("Parsed {} proxy candidate(s) from {} raw line(s)", proxies.size, lines.size)

        log.info("Fetched {} proxy candidates from {}", proxies.size, properties.proxy.sourceUrl)
        return proxies
    }

    private fun parseLine(line: String): ProxyCandidate? {
        val parts = line.split(":", limit = 2)
        if (parts.size != 2) return null
        val port = parts[1].trim().toIntOrNull() ?: return null
        return ProxyCandidate(parts[0].trim(), port)
    }
}
