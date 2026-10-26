package com.creditengine.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

@Component
class CreditEngineMetrics(
    private val registry: MeterRegistry,
) {
    fun <T> recordPricing(receivableType: String, currency: String, block: () -> T): T {
        val sample = Timer.start(registry)
        try {
            return block()
        } finally {
            sample.stop(pricingTimer(receivableType, currency))
        }
    }

    fun settlement(currency: String, status: String) {
        Counter.builder("credit_engine.settlements.total")
            .description("Total settlements grouped by payment currency and outcome")
            .tag("currency", currency)
            .tag("status", status)
            .register(registry)
            .increment()
    }

    private fun pricingTimer(receivableType: String, currency: String): Timer =
        Timer.builder("credit_engine.pricing.latency")
            .description("Latency of the pricing core algorithm grouped by type and currency")
            .tag("receivable_type", receivableType)
            .tag("currency", currency)
            .register(registry)
}
