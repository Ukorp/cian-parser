package ru.ukorp.parser.state

import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateRepositoryTest {

    private fun repository(tempDir: Path): StateRepository {
        val dataSource = DriverManagerDataSource("jdbc:sqlite:${tempDir.resolve("test.db")}")
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute(
            """
            CREATE TABLE chat_search_url (
                chat_id BIGINT NOT NULL,
                search_url TEXT NOT NULL,
                PRIMARY KEY (chat_id, search_url)
            )
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE seen_offer (
                chat_id BIGINT NOT NULL,
                search_url TEXT NOT NULL,
                offer_id TEXT NOT NULL,
                seen_order INTEGER NOT NULL,
                PRIMARY KEY (chat_id, search_url, offer_id)
            )
            """.trimIndent()
        )
        return StateRepository(jdbcTemplate)
    }

    @Test
    fun `loading from an empty database returns empty maps`(@TempDir tempDir: Path) {
        val repository = repository(tempDir)

        assertTrue(repository.loadSubscriptions().isEmpty())
        assertTrue(repository.loadSeenOffers().isEmpty())
    }

    @Test
    fun `save then load round-trips subscriptions and seen offers in order`(@TempDir tempDir: Path) {
        val repository = repository(tempDir)
        val subscriptions = mapOf(
            1L to listOf("https://cian.ru/a", "https://cian.ru/b"),
            2L to listOf("https://cian.ru/c"),
        )
        val seenOffers = mapOf(
            (1L to "https://cian.ru/a") to listOf("offer-1", "offer-2", "offer-3"),
            (2L to "https://cian.ru/c") to listOf("offer-9"),
        )

        repository.save(subscriptions, seenOffers)

        assertEquals(subscriptions, repository.loadSubscriptions())
        assertEquals(seenOffers, repository.loadSeenOffers())
    }

    @Test
    fun `save replaces previous snapshot entirely`(@TempDir tempDir: Path) {
        val repository = repository(tempDir)
        repository.save(
            mapOf(1L to listOf("https://cian.ru/a")),
            mapOf((1L to "https://cian.ru/a") to listOf("offer-1")),
        )

        repository.save(
            mapOf(1L to listOf("https://cian.ru/b")),
            mapOf((1L to "https://cian.ru/b") to listOf("offer-2")),
        )

        assertEquals(mapOf(1L to listOf("https://cian.ru/b")), repository.loadSubscriptions())
        assertEquals(mapOf((1L to "https://cian.ru/b") to listOf("offer-2")), repository.loadSeenOffers())
    }
}
