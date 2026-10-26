package com.creditengine.application.settlement

import com.creditengine.application.fx.ResilientFxService
import com.creditengine.application.rate.RateVigencyService
import com.creditengine.application.receivable.ReceivableNotFoundException
import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableStatus
import com.creditengine.infrastructure.metrics.CreditEngineMetrics
import com.creditengine.domain.pricing.PricingCommand
import com.creditengine.domain.pricing.PricingEngine
import com.creditengine.domain.pricing.TermCalculator
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.entity.SettlementEntity
import com.creditengine.infrastructure.persistence.repository.ReceivableRepository
import com.creditengine.infrastructure.persistence.repository.SettlementRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class SettleCommand(
    val receivableId: UUID,
    val idempotencyKey: UUID,
    val requestBodyHash: String,
)

data class SettleResult(
    val settlement: SettlementEntity,
    val replay: Boolean,
)

@Service
class SettlementService(
    private val receivableRepository: ReceivableRepository,
    private val settlementRepository: SettlementRepository,
    private val rateVigencyService: RateVigencyService,
    private val fxService: ResilientFxService,
    private val pricingEngine: PricingEngine = PricingEngine(),
    private val metrics: CreditEngineMetrics,
    private val writeGate: SettlementWriteGate,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)

    /**
     * Insert da liquidação e update do recebível na mesma transação (SPEC §4).
     * Qualquer falha desfaz as duas escritas. A leitura de replay fica fora dela,
     * para enxergar a linha já confirmada pelo request concorrente.
     */
    fun settle(command: SettleCommand): SettleResult {
        findReplay(command)?.let { return it }

        return try {
            val result = transactions.execute<SettleResult> { write(command) }
                ?: error("settlement transaction returned null")
            metrics.settlement(result.settlement.paymentCurrency.name, "success")
            result
        } catch (ex: DataIntegrityViolationException) {
            findReplay(command) ?: throw ReceivableAlreadySettledException(ex)
        }
    }

    private fun findReplay(command: SettleCommand): SettleResult? {
        val existing = settlementRepository.findByIdempotencyKey(command.idempotencyKey).orElse(null)
            ?: return null
        if (existing.requestBodyHash != command.requestBodyHash) {
            throw IdempotencyConflictException()
        }
        return SettleResult(existing, replay = true)
    }

    private fun write(command: SettleCommand): SettleResult {
        if (settlementRepository.findByReceivableId(command.receivableId).isPresent) {
            throw ReceivableAlreadySettledException()
        }

        val receivable = receivableRepository.findById(command.receivableId)
            .orElseThrow { ReceivableNotFoundException(command.receivableId) }

        if (receivable.status != ReceivableStatus.OPEN) {
            throw ReceivableAlreadySettledException()
        }

        writeGate.hold()

        return try {
            persist(command, receivable)
        } catch (ex: ObjectOptimisticLockingFailureException) {
            metrics.settlement(receivable.paymentCurrency.name, "failed")
            throw ex
        } catch (ex: RuntimeException) {
            metrics.settlement(receivable.paymentCurrency.name, "failed")
            throw ex
        }
    }

    private fun persist(command: SettleCommand, receivable: ReceivableEntity): SettleResult {
        val at = Instant.now()
        val reference = TermCalculator.referenceDate(at)
        val termMonths = TermCalculator.wholeMonthsBetween(reference, receivable.dueDate)
        val baseRate = rateVigencyService.effectiveBaseRate(at)
        val fx = if (receivable.paymentCurrency == PaymentCurrency.USD) {
            fxService.getLatestRate("USD")
        } else {
            null
        }

        val outcome = metrics.recordPricing(receivable.receivableType.name, receivable.paymentCurrency.name) {
            pricingEngine.price(
                PricingCommand(
                    faceValue = receivable.faceValue,
                    termMonths = termMonths,
                    receivableType = receivable.receivableType,
                    baseRate = baseRate,
                    paymentCurrency = receivable.paymentCurrency,
                    fxRateUsdBrl = fx,
                ),
            )
        }

        val settlement = SettlementEntity(
            receivableId = receivable.id,
            idempotencyKey = command.idempotencyKey,
            requestBodyHash = command.requestBodyHash,
            assignorId = receivable.assignorId,
            faceValue = receivable.faceValue,
            termMonths = termMonths,
            receivableType = receivable.receivableType,
            baseRate = baseRate,
            spread = outcome.spreadApplied,
            presentValueBrl = outcome.presentValueBrl,
            discountBrl = outcome.discountBrl,
            paymentAmount = outcome.paymentAmount,
            paymentCurrency = outcome.paymentCurrency,
            fxRateUsdBrl = outcome.fxRateUsed,
            settledAt = at,
        )

        receivable.status = ReceivableStatus.SETTLED
        receivableRepository.saveAndFlush(receivable)
        val saved = settlementRepository.saveAndFlush(settlement)
        return SettleResult(saved, replay = false)
    }
}

object SettlementBodyHasher {
    fun hash(receivableId: UUID): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val canonical = """{"receivableId":"$receivableId"}"""
        val bytes = digest.digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

class IdempotencyConflictException : RuntimeException("Idempotency-Key reused with different body")
class ReceivableAlreadySettledException(cause: Throwable? = null) :
    RuntimeException("Receivable already settled", cause)

class ConcurrentSettlementException(cause: Throwable) : RuntimeException("Concurrent settlement conflict", cause)
