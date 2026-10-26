package com.creditengine.presentation.api

import com.creditengine.application.receivable.CreateReceivableCommand
import com.creditengine.application.receivable.QuoteCommand
import com.creditengine.application.receivable.ReceivableService
import com.creditengine.application.settlement.SettleCommand
import com.creditengine.application.settlement.SettlementBodyHasher
import com.creditengine.application.settlement.SettlementService
import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.RatePair
import com.creditengine.infrastructure.persistence.report.SettlementReportRepository
import com.creditengine.presentation.dto.CreateReceivableRequest
import com.creditengine.presentation.dto.PublishRateRequest
import com.creditengine.presentation.dto.QuoteRequest
import com.creditengine.presentation.dto.SettleRequest
import com.creditengine.presentation.dto.SettlementReportResponse
import com.creditengine.presentation.mapper.toItem
import com.creditengine.presentation.mapper.toResponse
import com.creditengine.application.rate.RateVigencyService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api")
class QuoteController(
    private val receivableService: ReceivableService,
) {
    @PostMapping("/quotes")
    fun quote(@Valid @RequestBody request: QuoteRequest) =
        receivableService.quote(
            QuoteCommand(
                faceValue = request.faceValue,
                dueDate = request.dueDate,
                receivableType = request.receivableType,
                paymentCurrency = request.paymentCurrency,
            ),
        ).toResponse()
}

@RestController
@RequestMapping("/api/receivables")
class ReceivableController(
    private val receivableService: ReceivableService,
) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateReceivableRequest): ResponseEntity<*> {
        val entity = receivableService.create(
            CreateReceivableCommand(
                assignorId = request.assignorId,
                faceValue = request.faceValue,
                dueDate = request.dueDate,
                receivableType = request.receivableType,
                paymentCurrency = request.paymentCurrency,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(entity.toResponse())
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID) = receivableService.get(id).toResponse()
}

@RestController
class SettlementController(
    private val settlementService: SettlementService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/settlements")
    fun settle(
        @RequestHeader("Idempotency-Key") idempotencyKeyHeader: String?,
        @Valid @RequestBody request: SettleRequest,
    ): ResponseEntity<*> {
        val idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader)
        MDC.put("idempotencyKey", idempotencyKey.toString())
        return try {
            log.info("settlement requested receivableId={}", request.receivableId)
            val bodyHash = SettlementBodyHasher.hash(request.receivableId)
            val result = settlementService.settle(
                SettleCommand(
                    receivableId = request.receivableId,
                    idempotencyKey = idempotencyKey,
                    requestBodyHash = bodyHash,
                ),
            )
            val status = if (result.replay) HttpStatus.OK else HttpStatus.CREATED
            log.info("settlement completed status={} replay={}", status.value(), result.replay)
            ResponseEntity.status(status).body(result.settlement.toResponse())
        } finally {
            MDC.remove("idempotencyKey")
        }
    }

    private fun parseIdempotencyKey(raw: String?): UUID {
        if (raw.isNullOrBlank()) {
            throw IllegalArgumentException("Idempotency-Key header is required")
        }
        return try {
            UUID.fromString(raw.trim())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Idempotency-Key must be a UUID")
        }
    }

}

@RestController
@RequestMapping("/api/settlements")
class SettlementReportController(
    private val reportRepository: SettlementReportRepository,
) {
    @GetMapping("/report")
    fun report(
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) assignorId: String?,
        @RequestParam(required = false) paymentCurrency: PaymentCurrency?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): SettlementReportResponse {
        val boundedSize = size.coerceIn(1, 100)
        require(page >= 0) { "page must be >= 0" }
        val (rows, total) = reportRepository.findPage(from, to, assignorId, paymentCurrency, page, boundedSize)
        val totalPages = if (total == 0L) 0 else ((total + boundedSize - 1) / boundedSize).toInt()
        return SettlementReportResponse(
            items = rows.map { it.toItem() },
            page = page,
            size = boundedSize,
            totalElements = total,
            totalPages = totalPages,
        )
    }
}

@RestController
@RequestMapping("/api/rates")
class RateController(
    private val rateVigencyService: RateVigencyService,
) {
    @PostMapping("/base")
    fun publishBase(@Valid @RequestBody request: PublishRateRequest) =
        rateVigencyService.publish(RatePair.BASE_RATE, request.rate).let {
            mapOf("pair" to it.pair.name, "rate" to it.rate.toPlainString(), "effectiveAt" to it.effectiveAt.toString())
        }

    @PostMapping("/usd-brl")
    fun publishFx(@Valid @RequestBody request: PublishRateRequest) =
        rateVigencyService.publish(RatePair.USD_BRL, request.rate).let {
            mapOf("pair" to it.pair.name, "rate" to it.rate.toPlainString(), "effectiveAt" to it.effectiveAt.toString())
        }
}
