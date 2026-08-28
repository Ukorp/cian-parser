package ru.ukorp.parser.state

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Restores ChatSubscriptionStore/SeenOffersStore from disk on startup, then keeps them
 * durable by snapshotting periodically and once more on graceful shutdown.
 */
@Component
class StateSnapshotScheduler(
    private val seenOffersStore: SeenOffersStore,
    private val chatSubscriptionStore: ChatSubscriptionStore,
    private val stateRepository: StateRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun restoreOnStartup() {
        val subscriptions = stateRepository.loadSubscriptions()
        val seenOffers = stateRepository.loadSeenOffers()
        chatSubscriptionStore.restore(subscriptions)
        seenOffersStore.restore(seenOffers)
        log.info("Restored {} chat subscription(s) and {} seen-offer key(s) from disk", subscriptions.size, seenOffers.size)
    }

    @Scheduled(fixedDelayString = "#{@parserProperties.state.snapshotInterval.toMillis()}")
    fun snapshotPeriodically() = persist()

    @PreDestroy
    fun snapshotOnShutdown() = persist()

    private fun persist() {
        stateRepository.save(chatSubscriptionStore.all(), seenOffersStore.snapshot())
        log.debug("Persisted state snapshot to disk")
    }
}
