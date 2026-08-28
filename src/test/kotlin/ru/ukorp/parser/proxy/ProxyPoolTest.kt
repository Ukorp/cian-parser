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
    fun `returns null when no candidates are available`() {
        val pool = pool(emptyList())

        assertNull(pool.current())
    }

    @Test
    fun `round-robins through healthy candidates`() {
        val pool = pool(listOf(proxyA, proxyB))

        assertEquals(proxyA, pool.current())
        assertEquals(proxyB, pool.current())
        assertEquals(proxyA, pool.current())
    }

    @Test
    fun `markBad excludes a proxy from rotation`() {
        val pool = pool(listOf(proxyA, proxyB))
        pool.current() // trigger the initial fetch so candidates are populated before marking one bad

        pool.markBad(proxyA)

        assertEquals(proxyB, pool.current())
        assertEquals(proxyB, pool.current())
    }
}
