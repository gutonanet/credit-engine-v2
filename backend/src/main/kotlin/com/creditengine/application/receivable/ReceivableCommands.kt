package com.creditengine.application.receivable

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableType
import java.math.BigDecimal
import java.time.LocalDate

data class CreateReceivableCommand(
    val assignorId: String,
    val faceValue: BigDecimal,
    val dueDate: LocalDate,
    val receivableType: ReceivableType,
    val paymentCurrency: PaymentCurrency,
)

data class QuoteCommand(
    val faceValue: BigDecimal,
    val dueDate: LocalDate,
    val receivableType: ReceivableType,
    val paymentCurrency: PaymentCurrency,
)
