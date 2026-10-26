package com.creditengine.presentation.mapper

import com.creditengine.application.receivable.QuoteResult
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.entity.SettlementEntity
import com.creditengine.infrastructure.persistence.report.SettlementReportRow
import com.creditengine.presentation.dto.QuoteResponse
import com.creditengine.presentation.dto.ReceivableResponse
import com.creditengine.presentation.dto.SettlementReportItem
import com.creditengine.presentation.dto.SettlementResponse
import java.time.format.DateTimeFormatter

private val instantFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

fun ReceivableEntity.toResponse() = ReceivableResponse(
    id = id.toString(),
    assignorId = assignorId,
    faceValue = faceValue.toPlainString(),
    dueDate = dueDate,
    receivableType = receivableType,
    paymentCurrency = paymentCurrency,
    status = status.name,
)

fun QuoteResult.toResponse() = QuoteResponse(
    termMonths = termMonths,
    baseRate = baseRate,
    fxRateUsdBrl = fxRateUsdBrl,
    presentValueBrl = outcome.presentValueBrl.toPlainString(),
    discountBrl = outcome.discountBrl.toPlainString(),
    paymentAmount = outcome.paymentAmount.toPlainString(),
    paymentCurrency = outcome.paymentCurrency,
)

fun SettlementEntity.toResponse() = SettlementResponse(
    id = id.toString(),
    receivableId = receivableId.toString(),
    assignorId = assignorId,
    faceValue = faceValue.toPlainString(),
    termMonths = termMonths,
    receivableType = receivableType,
    baseRate = baseRate.toPlainString(),
    spread = spread.toPlainString(),
    presentValueBrl = presentValueBrl.toPlainString(),
    discountBrl = discountBrl.toPlainString(),
    paymentAmount = paymentAmount.toPlainString(),
    paymentCurrency = paymentCurrency,
    fxRateUsdBrl = fxRateUsdBrl?.toPlainString(),
    settledAt = instantFormatter.format(settledAt),
    idempotencyKey = idempotencyKey.toString(),
)

fun SettlementReportRow.toItem() = SettlementReportItem(
    id = id,
    receivableId = receivableId,
    assignorId = assignorId,
    faceValue = faceValue,
    presentValueBrl = presentValueBrl,
    discountBrl = discountBrl,
    paymentAmount = paymentAmount,
    paymentCurrency = paymentCurrency,
    fxRateUsdBrl = fxRateUsdBrl,
    settledAt = instantFormatter.format(settledAt),
)
