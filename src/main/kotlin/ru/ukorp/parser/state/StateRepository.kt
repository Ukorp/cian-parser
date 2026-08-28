package ru.ukorp.parser.state

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class StateRepository(private val jdbcTemplate: JdbcTemplate) {

    fun loadSubscriptions(): Map<Long, String> =
        jdbcTemplate.query("SELECT DISTINCT chat_id, search_url FROM chat_search_url") { rs, _ ->
            rs.getLong("chat_id") to rs.getString("search_url")
        }.toMap()

    fun loadSeenOffers(): Map<Pair<Long, String>, List<String>> =
        jdbcTemplate.query(
            "SELECT chat_id, search_url, offer_id FROM seen_offer ORDER BY chat_id, search_url, seen_order"
        ) { rs, _ ->
            (rs.getLong("chat_id") to rs.getString("search_url")) to rs.getString("offer_id")
        }.groupBy({ it.first }, { it.second })

    @Transactional
    fun save(subscriptions: Map<Long, String>, seenOffers: Map<Pair<Long, String>, List<String>>) {
        val subscriptionRows = subscriptions.toList()
        if (subscriptionRows.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT OR REPLACE INTO chat_search_url (chat_id, search_url) VALUES (?, ?)",
                subscriptionRows,
                subscriptionRows.size,
            ) { ps, row ->
                ps.setLong(1, row.first)
                ps.setString(2, row.second)
            }
        }

        val seenRows = seenOffers.flatMap { (key, offerIds) ->
            offerIds.mapIndexed { index, offerId -> Triple(key.first, key.second, offerId to index) }
        }
        if (seenRows.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO seen_offer (chat_id, search_url, offer_id, seen_order) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                seenRows,
                seenRows.size,
            ) { ps, row ->
                ps.setLong(1, row.first)
                ps.setString(2, row.second)
                ps.setString(3, row.third.first)
                ps.setInt(4, row.third.second)
            }
        }
    }
}
