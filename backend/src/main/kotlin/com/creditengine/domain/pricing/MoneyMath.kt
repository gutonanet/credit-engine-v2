package com.creditengine.domain.pricing

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object MoneyMath {
    val DECIMAL128: MathContext = MathContext.DECIMAL128
    val MONEY_SCALE: Int = 2
    val MONEY_ROUNDING: RoundingMode = RoundingMode.HALF_EVEN

    fun money(value: String): BigDecimal = BigDecimal(value).setScale(MONEY_SCALE, MONEY_ROUNDING)

    fun rate(value: String): BigDecimal = BigDecimal(value)

    fun roundMoney(value: BigDecimal): BigDecimal = value.setScale(MONEY_SCALE, MONEY_ROUNDING)
}
