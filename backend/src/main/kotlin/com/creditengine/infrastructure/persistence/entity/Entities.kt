package com.creditengine.infrastructure.persistence.entity

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.RatePair
import com.creditengine.domain.model.ReceivableStatus
import com.creditengine.domain.model.ReceivableType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "rate_vigencies")
class RateVigencyEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val pair: RatePair,

    @Column(nullable = false, precision = 18, scale = 8)
    val rate: BigDecimal,

    @Column(name = "effective_at", nullable = false)
    val effectiveAt: Instant,
)

@Entity
@Table(name = "receivables")
class ReceivableEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "assignor_id", nullable = false)
    val assignorId: String,

    @Column(name = "face_value", nullable = false, precision = 19, scale = 2)
    val faceValue: BigDecimal,

    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "receivable_type", nullable = false)
    val receivableType: ReceivableType,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_currency", nullable = false)
    val paymentCurrency: PaymentCurrency,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReceivableStatus = ReceivableStatus.OPEN,

    /** Optimistic lock (SPEC §4). Duas liquidações do mesmo recebível: uma confirma, a outra recebe 409. */
    @Version
    var version: Long = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "settlements")
class SettlementEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "receivable_id", nullable = false, unique = true)
    val receivableId: UUID,

    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: UUID,

    @Column(name = "request_body_hash", nullable = false)
    val requestBodyHash: String,

    @Column(name = "assignor_id", nullable = false)
    val assignorId: String,

    @Column(name = "face_value", nullable = false, precision = 19, scale = 2)
    val faceValue: BigDecimal,

    @Column(name = "term_months", nullable = false)
    val termMonths: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "receivable_type", nullable = false)
    val receivableType: ReceivableType,

    @Column(name = "base_rate", nullable = false, precision = 18, scale = 8)
    val baseRate: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    val spread: BigDecimal,

    @Column(name = "present_value_brl", nullable = false, precision = 19, scale = 2)
    val presentValueBrl: BigDecimal,

    @Column(name = "discount_brl", nullable = false, precision = 19, scale = 2)
    val discountBrl: BigDecimal,

    @Column(name = "payment_amount", nullable = false, precision = 19, scale = 2)
    val paymentAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_currency", nullable = false)
    val paymentCurrency: PaymentCurrency,

    @Column(name = "fx_rate_usd_brl", precision = 18, scale = 8)
    val fxRateUsdBrl: BigDecimal? = null,

    @Column(name = "settled_at", nullable = false)
    val settledAt: Instant = Instant.now(),
)
