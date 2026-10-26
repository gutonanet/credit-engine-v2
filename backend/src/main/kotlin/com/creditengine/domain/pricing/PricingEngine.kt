package com.creditengine.domain.pricing

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableType
import java.math.BigDecimal

data class PricingCommand(
    val faceValue: BigDecimal,
    val termMonths: Int,
    val receivableType: ReceivableType,
    val baseRate: BigDecimal,
    val paymentCurrency: PaymentCurrency,
    val fxRateUsdBrl: BigDecimal? = null,
)

data class PricingOutcome(
    val presentValueBrl: BigDecimal,
    val discountBrl: BigDecimal,
    val paymentAmount: BigDecimal,
    val paymentCurrency: PaymentCurrency,
    val spreadApplied: BigDecimal,
    val fxRateUsed: BigDecimal?,
)

class PricingEngine(
    private val strategies: PricingStrategyRegistry = PricingStrategyRegistry(),
) {
    fun price(command: PricingCommand): PricingOutcome {
        require(command.termMonths >= 1) { "termMonths must be >= 1" }
        require(command.faceValue > BigDecimal.ZERO) { "faceValue must be positive" }
        require(command.faceValue.scale() <= MoneyMath.MONEY_SCALE) {
            "faceValue must have at most 2 decimal places"
        }

        // Face já está na escala 2: setScale só completa zeros à direita, sem HALF_EVEN.
        // HALF_EVEN ocorre só no PV e, no pagamento USD, de novo no valor em dólar (SPEC §2).
        val face = command.faceValue.setScale(MoneyMath.MONEY_SCALE)
        val spread = strategies.forType(command.receivableType).spread
        val one = BigDecimal.ONE
        val rateSum = one.add(command.baseRate, MoneyMath.DECIMAL128).add(spread, MoneyMath.DECIMAL128)
        val factor = rateSum.pow(command.termMonths, MoneyMath.DECIMAL128)
        val presentValueRaw = face.divide(factor, MoneyMath.DECIMAL128)
        val presentValueBrl = MoneyMath.roundMoney(presentValueRaw)
        val discountBrl = face.subtract(presentValueBrl)

        return when (command.paymentCurrency) {
            PaymentCurrency.BRL -> PricingOutcome(
                presentValueBrl = presentValueBrl,
                discountBrl = discountBrl,
                paymentAmount = presentValueBrl,
                paymentCurrency = PaymentCurrency.BRL,
                spreadApplied = spread,
                fxRateUsed = null,
            )
            PaymentCurrency.USD -> {
                val fx = command.fxRateUsdBrl
                    ?: throw IllegalArgumentException("fxRateUsdBrl is required for USD payment")
                require(fx > BigDecimal.ZERO) { "fxRateUsdBrl must be positive" }
                val paymentUsdRaw = presentValueBrl.divide(fx, MoneyMath.DECIMAL128)
                val paymentUsd = MoneyMath.roundMoney(paymentUsdRaw)
                PricingOutcome(
                    presentValueBrl = presentValueBrl,
                    discountBrl = discountBrl,
                    paymentAmount = paymentUsd,
                    paymentCurrency = PaymentCurrency.USD,
                    spreadApplied = spread,
                    fxRateUsed = fx,
                )
            }
        }
    }

    /** Cross-currency with PV in BRL already rounded — used by the FX order policy test. */
    fun convertPresentValueToUsd(presentValueBrlRounded: BigDecimal, fxRateUsdBrl: BigDecimal): BigDecimal {
        val paymentUsdRaw = presentValueBrlRounded.divide(fxRateUsdBrl, MoneyMath.DECIMAL128)
        return MoneyMath.roundMoney(paymentUsdRaw)
    }
}
