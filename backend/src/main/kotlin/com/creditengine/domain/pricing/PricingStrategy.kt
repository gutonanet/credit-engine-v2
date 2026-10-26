package com.creditengine.domain.pricing

import com.creditengine.domain.model.ReceivableType
import java.math.BigDecimal

interface PricingStrategy {
    val spread: BigDecimal
}

class DuplicataStrategy : PricingStrategy {
    override val spread: BigDecimal = BigDecimal("0.015")
}

class ChequeStrategy : PricingStrategy {
    override val spread: BigDecimal = BigDecimal("0.025")
}

class PricingStrategyRegistry(
    private val duplicata: DuplicataStrategy = DuplicataStrategy(),
    private val cheque: ChequeStrategy = ChequeStrategy(),
) {
    fun forType(type: ReceivableType): PricingStrategy =
        when (type) {
            ReceivableType.DUPLICATA_MERCANTIL -> duplicata
            ReceivableType.CHEQUE_PREDATADO -> cheque
        }
}
