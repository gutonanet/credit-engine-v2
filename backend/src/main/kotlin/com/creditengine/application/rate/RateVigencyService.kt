package com.creditengine.application.rate

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.RatePair
import com.creditengine.infrastructure.persistence.entity.RateVigencyEntity
import com.creditengine.infrastructure.persistence.repository.RateVigencyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class RateVigencyService(
    private val repository: RateVigencyRepository,
) {
    fun effectiveBaseRate(at: Instant): BigDecimal =
        requireRate(RatePair.BASE_RATE, at).rate

    fun effectiveUsdBrl(at: Instant): BigDecimal =
        requireRate(RatePair.USD_BRL, at).rate

    fun requireRate(pair: RatePair, at: Instant): RateVigencyEntity =
        repository.findFirstByPairAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(pair, at)
            ?: throw MissingRateVigencyException(pair)

    @Transactional
    fun publish(pair: RatePair, rate: BigDecimal, effectiveAt: Instant = Instant.now()): RateVigencyEntity {
        require(rate > BigDecimal.ZERO) { "rate must be positive" }
        return repository.save(
            RateVigencyEntity(
                id = UUID.randomUUID(),
                pair = pair,
                rate = rate,
                effectiveAt = effectiveAt,
            ),
        )
    }
}

class MissingRateVigencyException(val pair: RatePair) : RuntimeException("No vigency for $pair")

fun fxRateForPayment(currency: PaymentCurrency, at: Instant, rates: RateVigencyService): BigDecimal? =
    if (currency == PaymentCurrency.USD) rates.effectiveUsdBrl(at) else null
