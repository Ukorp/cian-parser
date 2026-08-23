package ru.ukorp.parser.proxy

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import ru.ukorp.parser.config.ParserProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `rotates round-robin across healthy proxies`() {
        val pool = pool(listOf(proxyA, proxyB))

        assertEquals(proxyA, pool.current())
        assertEquals(proxyB, pool.current())
        assertEquals(proxyA, pool.current())
    }

    @Test
    fun `skips proxies marked bad`() {
        val pool = pool(listOf(proxyA, proxyB))

        assertEquals(proxyA, pool.current())
        pool.markBad(proxyA)

        assertEquals(proxyB, pool.current())
        assertEquals(proxyB, pool.current())
    }

    @Test
    fun `returns null when no candidates are available`() {
        val pool = pool(emptyList())

        assertNull(pool.current())
    }

    @Test
    fun `refetches once every candidate has been marked bad`() {
        val provider = mock(ProxyProvider::class.java)
        `when`(provider.fetch())
            .thenReturn(listOf(proxyA))
            .thenReturn(listOf(proxyB))
        val properties = ParserProperties()
        val pool = ProxyPool(provider, properties)

        assertEquals(proxyA, pool.current())
        pool.markBad(proxyA)

        assertEquals(proxyB, pool.current())
    }
}
