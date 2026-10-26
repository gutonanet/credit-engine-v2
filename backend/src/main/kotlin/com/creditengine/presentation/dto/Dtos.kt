package com.creditengine.presentation.dto

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class CreateReceivableRequest(
    @field:NotBlank val assignorId: String,
    @field:NotNull @field:DecimalMin("0.01") val faceValue: BigDecimal,
    @field:NotNull val dueDate: LocalDate,
    @field:NotNull val receivableType: ReceivableType,
    @field:NotNull val paymentCurrency: PaymentCurrency,
)

data class QuoteRequest(
    @field:NotNull @field:DecimalMin("0.01") val faceValue: BigDecimal,
    @field:NotNull val dueDate: LocalDate,
    @field:NotNull val receivableType: ReceivableType,
    @field:NotNull val paymentCurrency: PaymentCurrency,
)

data class QuoteResponse(
    val termMonths: Int,
    val baseRate: String,
    val fxRateUsdBrl: String?,
    val presentValueBrl: String,
    val discountBrl: String,
    val paymentAmount: String,
    val paymentCurrency: PaymentCurrency,
)

data class ReceivableResponse(
    val id: String,
    val assignorId: String,
    val faceValue: String,
    val dueDate: LocalDate,
    val receivableType: ReceivableType,
    val paymentCurrency: PaymentCurrency,
    val status: String,
)

data class SettleRequest(
    @field:NotNull val receivableId: java.util.UUID,
)

data class SettlementResponse(
    val id: String,
    val receivableId: String,
    val assignorId: String,
    val faceValue: String,
    val termMonths: Int,
    val receivableType: ReceivableType,
    val baseRate: String,
    val spread: String,
    val presentValueBrl: String,
    val discountBrl: String,
    val paymentAmount: String,
    val paymentCurrency: PaymentCurrency,
    val fxRateUsdBrl: String?,
    val settledAt: String,
    val idempotencyKey: String,
)

data class PublishRateRequest(
    @field:NotNull @field:DecimalMin("0.00000001") val rate: BigDecimal,
)

data class ErrorResponse(
    val code: String,
    val message: String,
    val field: String? = null,
)

data class SettlementReportResponse(
    val items: List<SettlementReportItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class SettlementReportItem(
    val id: String,
    val receivableId: String,
    val assignorId: String,
    val faceValue: String,
    val presentValueBrl: String,
    val discountBrl: String,
    val paymentAmount: String,
    val paymentCurrency: String,
    val fxRateUsdBrl: String?,
    val settledAt: String,
)
