package com.creditengine.domain.pricing

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** C1, C2 e C3 ao centavo, half-even e a ordem do câmbio (SPEC §5). */
class PricingEngineGoldenCasesTest {
    private val engine = PricingEngine()
    private val baseRate = BigDecimal("0.01")

    @Test
    fun `C1 duplicata 100k 3 months BRL`() {
        val outcome = engine.price(
            PricingCommand(
                faceValue = MoneyMath.money("100000.00"),
                termMonths = 3,
                receivableType = ReceivableType.DUPLICATA_MERCANTIL,
                baseRate = baseRate,
                paymentCurrency = PaymentCurrency.BRL,
            ),
        )
        assertEquals(MoneyMath.money("92859.94"), outcome.presentValueBrl)
        assertEquals(MoneyMath.money("7140.06"), outcome.discountBrl)
        assertEquals(MoneyMath.money("92859.94"), outcome.paymentAmount)
        assertEquals(PaymentCurrency.BRL, outcome.paymentCurrency)
    }

    @Test
    fun `C2 cheque 25k 2 months BRL`() {
        val outcome = engine.price(
            PricingCommand(
                faceValue = MoneyMath.money("25000.00"),
                termMonths = 2,
                receivableType = ReceivableType.CHEQUE_PREDATADO,
                baseRate = baseRate,
                paymentCurrency = PaymentCurrency.BRL,
            ),
        )
        assertEquals(MoneyMath.money("23337.77"), outcome.presentValueBrl)
        assertEquals(MoneyMath.money("1662.23"), outcome.discountBrl)
        assertEquals(MoneyMath.money("23337.77"), outcome.paymentAmount)
    }

    @Test
    fun `C3 duplicata 100k 3 months USD`() {
        val outcome = engine.price(
            PricingCommand(
                faceValue = MoneyMath.money("100000.00"),
                termMonths = 3,
                receivableType = ReceivableType.DUPLICATA_MERCANTIL,
                baseRate = baseRate,
                paymentCurrency = PaymentCurrency.USD,
                fxRateUsdBrl = BigDecimal("5.4321"),
            ),
        )
        assertEquals(MoneyMath.money("92859.94"), outcome.presentValueBrl)
        assertEquals(MoneyMath.money("7140.06"), outcome.discountBrl)
        assertEquals(MoneyMath.money("17094.67"), outcome.paymentAmount)
        assertEquals(PaymentCurrency.USD, outcome.paymentCurrency)
        assertEquals(BigDecimal("5.4321"), outcome.fxRateUsed)
    }

    @Test
    fun `half-even tie 1_005 rounds to 1_00`() {
        assertEquals(MoneyMath.money("1.00"), MoneyMath.roundMoney(BigDecimal("1.005")))
    }

    @Test
    fun `half-even tie 1_015 rounds to 1_02`() {
        assertEquals(MoneyMath.money("1.02"), MoneyMath.roundMoney(BigDecimal("1.015")))
    }

    @Test
    fun `FX order rounds BRL present value before dividing`() {
        val face = MoneyMath.money("100000.00")
        val spread = BigDecimal("0.015")
        val rateSum = BigDecimal.ONE.add(baseRate).add(spread)
        val factor = rateSum.pow(3, MoneyMath.DECIMAL128)
        val presentValueRaw = face.divide(factor, MoneyMath.DECIMAL128)
        val presentValueBrl = MoneyMath.roundMoney(presentValueRaw)
        val fx = BigDecimal("5.0037")
        val paymentUsd = engine.convertPresentValueToUsd(presentValueBrl, fx)
        assertEquals(MoneyMath.money("18558.25"), paymentUsd)
    }
}
