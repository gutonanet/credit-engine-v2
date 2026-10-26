package com.creditengine.application.receivable

import com.creditengine.application.fx.ResilientFxService
import com.creditengine.application.rate.RateVigencyService
import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.pricing.PricingCommand
import com.creditengine.infrastructure.metrics.CreditEngineMetrics
import com.creditengine.domain.pricing.PricingEngine
import com.creditengine.domain.pricing.PricingOutcome
import com.creditengine.domain.pricing.TermCalculator
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.repository.ReceivableRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class QuoteResult(
    val termMonths: Int,
    val baseRate: String,
    val fxRateUsdBrl: String?,
    val outcome: PricingOutcome,
)

@Service
class ReceivableService(
    private val receivableRepository: ReceivableRepository,
    private val rateVigencyService: RateVigencyService,
    private val fxService: ResilientFxService,
    private val metrics: CreditEngineMetrics,
    private val pricingEngine: PricingEngine = PricingEngine(),
) {
    @Transactional
    fun create(command: CreateReceivableCommand): ReceivableEntity {
        validateFace(command.faceValue)
        val reference = TermCalculator.referenceDate()
        TermCalculator.wholeMonthsBetween(reference, command.dueDate)

        return receivableRepository.save(
            ReceivableEntity(
                assignorId = command.assignorId.trim(),
                faceValue = command.faceValue,
                dueDate = command.dueDate,
                receivableType = command.receivableType,
                paymentCurrency = command.paymentCurrency,
            ),
        )
    }

    fun quote(command: QuoteCommand, at: Instant = Instant.now()): QuoteResult {
        validateFace(command.faceValue)
        val reference = TermCalculator.referenceDate(at)
        val termMonths = TermCalculator.wholeMonthsBetween(reference, command.dueDate)
        val baseRate = rateVigencyService.effectiveBaseRate(at)
        val fx = if (command.paymentCurrency == PaymentCurrency.USD) fxService.getLatestRate("USD") else null
        val outcome = metrics.recordPricing(command.receivableType.name, command.paymentCurrency.name) {
            pricingEngine.price(
                PricingCommand(
                    faceValue = command.faceValue,
                    termMonths = termMonths,
                    receivableType = command.receivableType,
                    baseRate = baseRate,
                    paymentCurrency = command.paymentCurrency,
                    fxRateUsdBrl = fx,
                ),
            )
        }
        return QuoteResult(
            termMonths = termMonths,
            baseRate = baseRate.toPlainString(),
            fxRateUsdBrl = fx?.toPlainString(),
            outcome = outcome,
        )
    }

    fun get(id: UUID): ReceivableEntity =
        receivableRepository.findById(id).orElseThrow { ReceivableNotFoundException(id) }

    private fun validateFace(faceValue: java.math.BigDecimal) {
        require(faceValue > java.math.BigDecimal.ZERO) { "faceValue must be positive" }
        require(faceValue.scale() <= 2) { "faceValue must have at most 2 decimal places" }
    }
}

class ReceivableNotFoundException(id: UUID) : RuntimeException("Receivable $id not found")
