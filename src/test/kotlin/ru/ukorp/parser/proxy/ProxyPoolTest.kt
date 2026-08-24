package ru.ukorp.parser.proxy

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import ru.ukorp.parser.config.ParserProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertNull

class ProxyPoolTest {

    private val proxyA = ProxyCandidate("1.1.1.1", 8080)
    private val proxyB = ProxyCandidate("2.2.2.2", 8080)

    private fun pool(proxies: List<ProxyCandidate>, refreshInterval: Duration = Duration.ofMinutes(30)): ProxyPool {
        val provider = mock(ProxyProvider::class.java)
        `when`(provider.fetch()).thenReturn(proxies)
        val properties = ParserProperties().apply { proxy.refreshInterval = refreshInterval }
        return ProxyPool(provider, properties)
    }

    @Test
    fun `returns null when no candidates are available`() {
        val pool = pool(emptyList())

        assertNull(pool.current())
    }

    @Test
    fun `returns null even with healthy candidates while proxy rotation is disabled`() {
        val pool = pool(listOf(proxyA, proxyB))

        assertNull(pool.current())
        assertNull(pool.current())
    }

    @Test
    fun `markBad does not throw while proxy rotation is disabled`() {
        val pool = pool(listOf(proxyA, proxyB))

        pool.markBad(proxyA)

        assertNull(pool.current())
    }
}
